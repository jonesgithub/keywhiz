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

package keywhiz.service.daos;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import org.jooq.DSLContext;
import org.jooq.tools.json.JSONObject;

import static keywhiz.jooq.tables.SecretsContent.SECRETS_CONTENT;

/**
 * Jooq version of SecretContentDAO.
 */
public class SecretContentJooqDao {
  private final DSLContext dslContext;

  @Inject
  public SecretContentJooqDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  public long createSecretContent(long secretId, String encryptedContent, String version,
      String creator, Map<String, String> metadata) {
    SecretsContentRecord r = dslContext.newRecord(SECRETS_CONTENT);

    r.setSecretid((int)secretId);
    r.setEncryptedContent(encryptedContent);
    r.setVersion(version);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setMetadata(new JSONObject(metadata).toString());
    r.store();

    return r.getId();
  }

  public Optional<SecretContent> getSecretContentById(long id) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT, SECRETS_CONTENT.ID.eq((int) id));
    if (r != null) {
      return Optional.of(r.map(new SecretContentJooqMapper()));
    }
    return Optional.empty();
  }

  public Optional<SecretContent> getSecretContentBySecretIdAndVersion(long secretId, String version) {
    SecretsContentRecord r = dslContext.fetchOne(
        SECRETS_CONTENT,
        SECRETS_CONTENT.SECRETID.eq((int) secretId).and(SECRETS_CONTENT.VERSION.eq(version)));
    if (r != null) {
      return Optional.of(r.map(new SecretContentJooqMapper()));
    }
    return Optional.empty();
  }

  public ImmutableList<SecretContent> getSecretContentsBySecretId(long secretId) {
    List<SecretContent> r = dslContext
        .select()
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq((int) secretId))
        .fetch()
        .map(new SecretContentJooqMapper());

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretContentBySecretIdAndVersion(long secretId, String version) {
    SecretsContentRecord r = dslContext.fetchOne(SECRETS_CONTENT,
        SECRETS_CONTENT.SECRETID.eq((int) secretId).and(SECRETS_CONTENT.VERSION.eq(version)));
    r.delete();
  }

  public ImmutableList<String> getVersionFromSecretId(long secretId) {
    List<String> r = dslContext
        .select(SECRETS_CONTENT.VERSION)
        .from(SECRETS_CONTENT)
        .where(SECRETS_CONTENT.SECRETID.eq((int) secretId))
        .fetch(SECRETS_CONTENT.VERSION);

    return ImmutableList.copyOf(r);
  }
}
