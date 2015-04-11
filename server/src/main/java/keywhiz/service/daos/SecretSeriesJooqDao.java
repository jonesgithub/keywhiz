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
import javax.annotation.Nullable;
import javax.inject.Inject;
import keywhiz.api.model.SecretSeries;
import keywhiz.jooq.tables.records.SecretsRecord;
import org.jooq.DSLContext;
import org.jooq.tools.json.JSONObject;

import static keywhiz.jooq.tables.Secrets.SECRETS;

class SecretSeriesJooqDao {
  private final DSLContext dslContext;

  @Inject
  public SecretSeriesJooqDao(DSLContext dslContext) {
    this.dslContext = dslContext;
  }

  long createSecretSeries(String name, String creator, String description, @Nullable String type,
      @Nullable Map<String, String> generationOptions) {
    SecretsRecord r =  dslContext.newRecord(SECRETS);

    r.setName(name);;
    r.setDescription(description);
    r.setCreatedby(creator);
    r.setUpdatedby(creator);
    r.setType(type);
    if (generationOptions != null) {
      r.setOptions(JSONObject.toJSONString(generationOptions));
    } else {
      r.setOptions("{}");
    }
    r.store();

    return r.getId();
  }

  Optional<SecretSeries> getSecretSeriesById(long id) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.ID.eq((int) id));
    if (r != null) {
      return Optional.of(r.map(new SecretSeriesJooqMapper()));
    }
    return Optional.empty();
  }

  Optional<SecretSeries> getSecretSeriesByName(String name) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(name));
    if (r != null) {
      return Optional.of(r.map(new SecretSeriesJooqMapper()));
    }
    return Optional.empty();
  }

  public ImmutableList<SecretSeries> getSecretSeries() {
    List<SecretSeries> r = dslContext
        .select()
        .from(SECRETS)
        .fetch()
        .map(new SecretSeriesJooqMapper());

    return ImmutableList.copyOf(r);
  }

  public void deleteSecretSeriesByName(String name) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.NAME.eq(name));
    r.delete();
  }

  public void deleteSecretSeriesById(long id) {
    SecretsRecord r = dslContext.fetchOne(SECRETS, SECRETS.ID.eq((int) id));
    r.delete();
  }
}
