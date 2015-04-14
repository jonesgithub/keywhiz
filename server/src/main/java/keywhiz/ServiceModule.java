/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package keywhiz;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.Configuration;
import io.dropwizard.auth.basic.BasicCredentials;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.java8.auth.Authenticator;
import io.dropwizard.setup.Environment;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import keywhiz.auth.BouncyCastle;
import keywhiz.auth.User;
import keywhiz.auth.cookie.CookieConfig;
import keywhiz.auth.cookie.CookieModule;
import keywhiz.auth.cookie.SessionCookie;
import keywhiz.auth.xsrf.Xsrf;
import keywhiz.generators.SecretGeneratorBindingModule;
import keywhiz.generators.TemplatedSecretGenerator;
import keywhiz.service.config.Readonly;
import keywhiz.service.crypto.ContentCryptographer;
import keywhiz.service.crypto.CryptoModule;
import keywhiz.service.crypto.SecretTransformer;
import keywhiz.service.daos.AclJooqDao;
import keywhiz.service.daos.ClientJooqDao;
import keywhiz.service.daos.GroupJooqDao;
import keywhiz.service.daos.SecretContentJooqDao;
import keywhiz.service.daos.SecretController;
import keywhiz.service.daos.SecretJooqDao;
import keywhiz.service.daos.SecretSeriesJooqDao;
import keywhiz.service.daos.UserJooqDao;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class ServiceModule extends AbstractModule {
  private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ServiceModule.class);

  private final Environment environment;
  private final KeywhizConfig config;

  public ServiceModule(KeywhizConfig config, Environment environment) {
    this.config = checkNotNull(config);
    this.environment = checkNotNull(environment);
  }

  @Override protected void configure() {
    // Initialize the BouncyCastle security provider for BKS and cryptography support.
    BouncyCastle.require();

    bind(Clock.class).toInstance(Clock.systemUTC());

    install(new CookieModule(config.getCookieKey()));
    install(new CryptoModule(config.getDerivationProviderClass(), config.getContentKeyStore()));

    bind(CookieConfig.class).annotatedWith(SessionCookie.class)
        .toInstance(config.getSessionCookieConfig());
    bind(CookieConfig.class).annotatedWith(Xsrf.class)
        .toInstance(config.getXsrfCookieConfig());

    // TODO(justin): Consider https://github.com/HubSpot/dropwizard-guice.
    bind(Environment.class).toInstance(environment);
    bind(Configuration.class).toInstance(config);
    bind(KeywhizConfig.class).toInstance(config);

    install(new SecretGeneratorBindingModule() {
      @Override protected void configure() {
        bindSecretGenerator("templated", TemplatedSecretGenerator.class);
      }
    });

    // Add Dropwizard health check for readonly database
    environment.healthChecks().register("postgres-readonly-health", new HealthCheck() {
      @Override protected Result check() {
        try {
          Connection connection = readonlyDataSource(environment, config).getConnection();
          DSLContext readOnlyDB = DSL.using(connection);
          readOnlyDB.selectOne().execute();
          connection.close();
          return Result.healthy();
        } catch (SQLException e) {
          return Result.unhealthy("Unhealthy connection to readonly database.");
        }
      }
    });
  }

  @Provides ObjectMapper configuredObjectMapper(Environment environment) {
    return environment.getObjectMapper();
  }

  @Provides @Singleton ManagedDataSource writableDataSource(Environment environment, KeywhizConfig config) {
    DataSourceFactory dataSourceFactory = config.getDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(), "postgres-writable");
    environment.lifecycle().manage(dataSource);

    return dataSource;
  }

  @Provides @Singleton
  @Readonly ManagedDataSource readonlyDataSource(Environment environment, KeywhizConfig config) {
    logger.debug("Creating read-only data source");
    DataSourceFactory dataSourceFactory = config.getReadonlyDataSourceFactory();
    ManagedDataSource dataSource = dataSourceFactory.build(environment.metrics(),
        "postgres-readonly");
    // TODO: do we need to do environment.lifecycle().manage(dataSource)?
    return dataSource;
  }

  @Provides @Singleton @Readonly SecretController readonlySecretController(
      SecretTransformer transformer, ContentCryptographer cryptographer,
      @Readonly SecretJooqDao secretJooqDao) {
    return new SecretController(transformer, cryptographer, secretJooqDao);
  }

  @Provides @Singleton SecretController secretController(SecretTransformer transformer,
      ContentCryptographer cryptographer, SecretJooqDao secretJooqDao) {
    return new SecretController(transformer, cryptographer, secretJooqDao);
  }

  @Provides @Singleton DSLContext jooqContext(ManagedDataSource dataSource) throws SQLException {
    return DSL.using(dataSource.getConnection());
  }

  @Provides @Singleton ClientJooqDao clientJooqDao(DSLContext jooqContext) {
    return new ClientJooqDao(jooqContext);
  }

  @Provides @Singleton GroupJooqDao groupJooqDao(DSLContext jooqContext) {
    return new GroupJooqDao(jooqContext);
  }

  @Provides @Singleton SecretContentJooqDao secretContentJooqDao(DSLContext jooqContext) {
    return new SecretContentJooqDao(jooqContext);
  }

  @Provides @Singleton SecretSeriesJooqDao secretSeriesJooqDao(DSLContext jooqContext) {
    return new SecretSeriesJooqDao(jooqContext);
  }

  @Provides @Singleton AclJooqDao aclJooqDao(DSLContext jooqContext, ClientJooqDao clientJooqDao,
      GroupJooqDao groupJooqDao, SecretContentJooqDao secretContentJooqDao,
      SecretSeriesJooqDao secretSeriesJooqDao) {
    return new AclJooqDao(jooqContext, clientJooqDao, groupJooqDao, secretContentJooqDao,
        secretSeriesJooqDao);
  }

  @Provides @Singleton @Readonly DSLContext readonlyJooqContext(/* @Readonly but PSql only allows 1 connection? */ ManagedDataSource dataSource) throws SQLException {
    return DSL.using(dataSource.getConnection());
  }

  @Provides @Singleton @Readonly ClientJooqDao readonlyClientJooqDao(@Readonly DSLContext jooqContext) {
    return new ClientJooqDao(jooqContext);
  }

  @Provides @Singleton @Readonly GroupJooqDao readonlyGroupJooqDao(@Readonly DSLContext jooqContext) {
    return new GroupJooqDao(jooqContext);
  }

  @Provides @Singleton @Readonly SecretContentJooqDao readonlySecretContentJooqDao(@Readonly DSLContext jooqContext) {
    return new SecretContentJooqDao(jooqContext);
  }

  @Provides @Singleton @Readonly SecretSeriesJooqDao readonlySecretSeriesJooqDao(@Readonly DSLContext jooqContext) {
    return new SecretSeriesJooqDao(jooqContext);
  }

  @Provides @Singleton @Readonly SecretJooqDao readonlySecretJooqDao(@Readonly DSLContext jooqContext,
      @Readonly SecretContentJooqDao secretContentJooqDao, @Readonly SecretSeriesJooqDao secretSeriesDao) {
    return new SecretJooqDao(jooqContext, secretContentJooqDao, secretSeriesDao);
  }

  @Provides @Singleton @Readonly AclJooqDao readonlyAclJooqDao(@Readonly DSLContext jooqContext,
      @Readonly ClientJooqDao clientJooqDao, @Readonly GroupJooqDao groupJooqDao,
      @Readonly SecretContentJooqDao secretContentJooqDao,
      @Readonly SecretSeriesJooqDao secretSeriesJooqDao) {
    return new AclJooqDao(jooqContext, clientJooqDao, groupJooqDao, secretContentJooqDao,
        secretSeriesJooqDao);
  }

  // Should be readonly jooqContext?
  @Provides @Singleton Authenticator<BasicCredentials, User> authenticator(KeywhizConfig config, DSLContext jooqContext) {
    UserJooqDao userJooqDao = new UserJooqDao(jooqContext);
    return config.getUserAuthenticatorFactory().build(userJooqDao);
  }
}
