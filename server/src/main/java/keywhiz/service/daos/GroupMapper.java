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

import keywhiz.api.model.Group;
import keywhiz.jooq.tables.records.GroupsRecord;
import org.jooq.RecordMapper;

class GroupMapper implements RecordMapper<GroupsRecord, Group> {
  public Group map(GroupsRecord r) {
    return new Group(
        r.getId(),
        r.getName(),
        r.getDescription(),
        r.getCreatedat(),
        r.getCreatedby(),
        r.getUpdatedat(),
        r.getUpdatedby());
  }
}
