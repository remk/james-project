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

import java.util.Optional;

import org.apache.commons.compress.archivers.zip.ZipShort;

public class EntryTypeExtraField extends LongExtraField implements WithZipHeader {

    public static final ZipShort ID_AQ = new ZipShort(WithZipHeader.toLittleEndian('a', 'q'));
    public static final EntryTypeExtraField TYPE_MAILBOX = new EntryTypeExtraField(ZipEntryType.MAILBOX);
    public static final EntryTypeExtraField TYPE_MAILBOX_ANNOTATION_DIR = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION_DIR);
    public static final EntryTypeExtraField TYPE_MAILBOX_ANNOTATION = new EntryTypeExtraField(ZipEntryType.MAILBOX_ANNOTATION);
    public static final EntryTypeExtraField TYPE_MESSAGE = new EntryTypeExtraField(ZipEntryType.MESSAGE);

    /**
     * Only for ExtraFieldUtils.register
     */
    public EntryTypeExtraField() {
        super();
    }

    public EntryTypeExtraField(ZipEntryType entryType) {
        super(entryType.getValue());
    }

    public Optional<ZipEntryType> getEnumValue() throws IllegalArgumentException {
        return getValue().flatMap(ZipEntryType::getFromValue);
    }

    @Override
    public ZipShort getHeaderId() {
        return ID_AQ;
    }
}
