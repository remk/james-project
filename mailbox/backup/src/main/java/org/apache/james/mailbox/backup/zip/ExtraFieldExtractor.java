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

public class ExtraFieldExtractor {

    private static <E, T> Optional<T> extractField(ZipShort id, ZipEntry entry, Function<Object, E> castExtraField, Function<E, Optional<T>> readValue) throws ZipException {
        ZipExtraField[] extraFields = ExtraFieldUtils.parse(entry.getExtra());
        return Arrays.stream(extraFields)
            .filter(field -> field.getHeaderId().equals(id))
            .map(castExtraField)
            .map(readValue)
            .findFirst()
            .flatMap(Function.identity());
    }

    public static Optional<Long> getLongExtraField(ZipShort id, ZipEntry entry) throws ZipException {
        return extractField(id, entry, LongExtraField.class::cast, LongExtraField::getValue);
    }

    public static Optional<String> getStringExtraField(ZipShort id, ZipEntry entry) throws ZipException {
        return extractField(id, entry, StringExtraField.class::cast, StringExtraField::getValue);
    }

    public static Optional<ZipEntryType> getEntryType(ZipEntry entry) {
        try {
            return extractField(EntryTypeExtraField.ID_AQ, entry, EntryTypeExtraField.class::cast, EntryTypeExtraField::getEnumValue);
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

    public static Optional<Flags> getFlags(ZipEntry entry) throws ZipException {
        return getStringExtraField(FlagsExtraField.ID_AP, entry).map(Flags::new);
    }

}
