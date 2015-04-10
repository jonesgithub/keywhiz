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

import com.google.common.collect.ImmutableMap;
import keywhiz.api.model.SecretContent;
import keywhiz.jooq.tables.records.SecretsContentRecord;
import org.jooq.Record;
import org.jooq.RecordMapper;

class SecretContentJooqMapper implements RecordMapper<Record, SecretContent> {
  public SecretContent map(Record record) {
    SecretsContentRecord r = (SecretsContentRecord) record;

    return SecretContent.of(r.getId(), r.getSecretid(), r.getEncryptedContent(), r.getVersion(),
        r.getCreatedat(), r.getCreatedby(), r.getUpdatedat(), r.getUpdatedby(),
        ImmutableMap.of() /* TODO: handle metadata */);
  }
}
