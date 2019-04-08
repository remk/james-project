/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/
package org.apache.james.mailbox.backup.zip;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import javax.mail.Flags;

import org.apache.commons.compress.archivers.zip.ExtraFieldUtils;
import org.apache.commons.compress.archivers.zip.ZipExtraField;
import org.apache.commons.compress.archivers.zip.ZipShort;
import org.apache.james.mailbox.backup.SerializedMailboxId;
import org.apache.james.mailbox.backup.SerializedMessageId;
import org.apache.james.util.OptionalUtils;

public class ExtraFieldExtractor {

    public static Optional<Long> getLongExtraField(ZipShort id, ZipEntry entry) throws ZipException {
        ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
        return Arrays.stream(extraFields)
            .filter(field -> field.getHeaderId().equals(id))
            .map(extraField -> ((LongExtraField) extraField).getValue())
            .findFirst()
            .flatMap(Function.identity());
    }

    public static Optional<String> getStringExtraField(ZipShort id, ZipEntry entry) throws ZipException {
        ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
        return Arrays.stream(extraFields)
            .filter(field -> field.getHeaderId().equals(id))
            .map(extraField -> ((StringExtraField) extraField).getValue())
            .findFirst()
            .flatMap(Function.identity());
    }

    public static Optional<ZipEntryType> getEntryType(ZipEntry entry) {
        try {
            ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
            return Arrays.stream(extraFields)
                .filter(field -> field.getHeaderId().equals(EntryTypeExtraField.ID_AQ))
                .flatMap(extraField ->
                    OptionalUtils.toStream(((EntryTypeExtraField) extraField).getEnumValue()))
                .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public static Optional<SerializedMailboxId> getMailBoxId(ZipEntry entry) throws ZipException {
        return getStringExtraField(MailboxIdExtraField.ID_AM, entry).map(SerializedMailboxId::new);
    }

    public static Optional<SerializedMessageId> getMessageId(ZipEntry entry) throws ZipException {
        return getStringExtraField(MessageIdExtraField.ID_AL, entry).map(SerializedMessageId::new);
    }

    public static Optional<Long> getSize(ZipEntry entry) throws ZipException {
        return getLongExtraField(SizeExtraField.ID_AJ, entry);
    }

    public static Optional<Date> getInternalDate(ZipEntry entry) throws ZipException {
        ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
        return Arrays.stream(extraFields)
            .filter(field -> field.getHeaderId().equals(InternalDateExtraField.ID_AO))
            .map(extraField -> ((InternalDateExtraField) extraField).getDateValue())
            .findFirst()
            .flatMap(Function.identity());
    }

    public static Optional<Flags> getFlags(ZipEntry entry) throws ZipException {
        return getStringExtraField(FlagsExtraField.ID_AP, entry).map(Flags::new);
    }
    
}
