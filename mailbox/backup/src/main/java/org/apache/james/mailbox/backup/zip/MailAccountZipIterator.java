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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import org.apache.james.mailbox.backup.MailArchiveEntry;
import org.apache.james.mailbox.backup.MailArchiveIterator;
import org.apache.james.mailbox.backup.MailboxWithAnnotationsArchiveEntry;
import org.apache.james.mailbox.backup.SerializedMailboxId;
import org.apache.james.mailbox.backup.UnknownArchiveEntry;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;

public class MailAccountZipIterator implements MailArchiveIterator {
    private static final Logger LOGGER = LoggerFactory.getLogger(MailAccountZipIterator.class);
    private static final List<MailboxAnnotation> NO_ANNOTATION = ImmutableList.of();
    public static final String EMPTY_ANNOTATION_CONTENT = "";
    private final ZipEntryIterator zipEntryIterator;
    private Optional<MailboxWithAnnotationsArchiveEntry> currentMailBox;
    private Optional<ZipEntryWithContent> next;

    public MailAccountZipIterator(ZipEntryIterator zipEntryIterator) {
        this.zipEntryIterator = zipEntryIterator;
        next = Optional.ofNullable(zipEntryIterator.next());
    }

    @Override
    public void close() throws IOException {
        zipEntryIterator.close();
    }

    @Override
    public boolean hasNext() {
        return next.isPresent();
    }

    @Override
    public MailArchiveEntry next() {
        return next.map(this::doNext).orElse(null);
    }

    private MailArchiveEntry doNext(ZipEntryWithContent currentElement) {
        next = Optional.ofNullable(zipEntryIterator.next());
        Optional<ZipEntry> nextZipEntry = next.map(ZipEntryWithContent::getEntry);
        try {
            return getMailArchiveEntry(currentElement, nextZipEntry);
        } catch (Exception e) {
            LOGGER.error("Error when reading archive on entry : " + currentElement.getEntryName(), e);
            next = Optional.empty();
            return new UnknownArchiveEntry(currentElement.getEntryName());
        }
    }

    private MailArchiveEntry getMailArchiveEntry(ZipEntryWithContent currentElement, Optional<ZipEntry> nextZipEntry) throws Exception {
        Optional<ZipEntryType> entryType = ExtraFieldExtractor.getEntryType(currentElement.getEntry());
        return entryType
            .map(Throwing.<ZipEntryType, MailArchiveEntry>function(type ->
                from(currentElement, type, nextZipEntry)).sneakyThrow()
            )
            .orElseGet(() -> new UnknownArchiveEntry(currentElement.getEntryName()));
    }

    private Optional<SerializedMailboxId> getMailBoxId(ZipEntry entry) throws ZipException {
        return ExtraFieldExtractor.getStringExtraField(MailboxIdExtraField.ID_AM, entry).map(SerializedMailboxId::new);
    }

    private String getMailboxName(ZipEntry current) {
        return current.getName().substring(0, current.getName().length() - 1);
    }

    private MailArchiveEntry fromMailboxEntry(ZipEntryWithContent current, Optional<ZipEntry> nextZipEntry) throws ZipException {
        ZipEntry entry = current.getEntry();
        if (isLastEntryOrHasNoAnnotationsDirectory(nextZipEntry)) {
            //no annotation directory or end of iterator => get Mailbox
            currentMailBox = Optional.empty();
            return new MailboxWithAnnotationsArchiveEntry(getMailboxName(entry), getMailBoxId(entry).get(), NO_ANNOTATION);
        } else {
            // annotation directory => save current mailbox and fetch next entry
            currentMailBox = Optional.of(new MailboxWithAnnotationsArchiveEntry(getMailboxName(entry), getMailBoxId(entry).get(), NO_ANNOTATION));
            return next();
        }
    }

    private boolean isOfType(ZipEntry nextZipEntry, ZipEntryType expectedType) {
        Optional<ZipEntryType> zipEntryType = ExtraFieldExtractor.getEntryType(nextZipEntry);

        return zipEntryType
            .map(actualType -> actualType.equals(expectedType))
            .orElseGet(() -> false);
    }

    private boolean isLastEntryOrNextEntryIsNotOfType(Optional<ZipEntry> nextZipEntry, ZipEntryType zipEntryType) throws ZipException {
        return !nextZipEntry.isPresent() || !isOfType(nextZipEntry.get(), zipEntryType);
    }

    private boolean isLastEntryOrHasNoAnnotationsDirectory(Optional<ZipEntry> nextZipEntry) throws ZipException {
        return isLastEntryOrNextEntryIsNotOfType(nextZipEntry, ZipEntryType.MAILBOX_ANNOTATION_DIR);
    }

    private boolean isLastEntryOrNextEntryIsNotOfType(Optional<ZipEntry> nextZipEntry) throws ZipException {
        return isLastEntryOrNextEntryIsNotOfType(nextZipEntry, ZipEntryType.MAILBOX_ANNOTATION);
    }

    private MailArchiveEntry fromMailboxAnnotationEntry(ZipEntryWithContent current, Optional<ZipEntry> next) throws ZipException {
        MailboxAnnotation annotation = MailboxAnnotation.newInstance(getAnnotationKey(currentMailBox.get(), current.getEntry()),
            getAnnotationContent(current).orElse(EMPTY_ANNOTATION_CONTENT));
        MailboxWithAnnotationsArchiveEntry mailboxUpdated = currentMailBox.get().appendAnnotation(annotation);

        if (isLastEntryOrNextEntryIsNotOfType(next)) {
            currentMailBox = Optional.empty();
            return mailboxUpdated;
        } else {
            currentMailBox = Optional.of(mailboxUpdated);
            return next();
        }
    }

    private Optional<byte[]> getCurrentEntryContentBytes(ByteSource currentContent) {
        try {
            return Optional.ofNullable(currentContent.read());
        } catch (IOException e) {
            LOGGER.error("Error when reading entry content", e);
            return Optional.empty();
        }
    }

    private Optional<String> getAnnotationContent(ZipEntryWithContent entry) {
        return entry.getContent()
            .flatMap(content -> getCurrentEntryContentBytes(content))
            .map(raw -> new String(raw, Charsets.UTF_8));
    }

    private MailboxAnnotationKey getAnnotationKey(MailboxWithAnnotationsArchiveEntry mailbox, ZipEntry entry) {
        String key = entry.getName().substring((mailbox.getMailboxName() + "/" + Zipper.ANNOTATION_DIRECTORY + "/").length());
        return new MailboxAnnotationKey(key);
    }

    private MailArchiveEntry from(ZipEntryWithContent current, ZipEntryType currentEntryType, Optional<ZipEntry> nextZipEntry) throws ZipException {
        switch (currentEntryType) {
            case MAILBOX:
                return fromMailboxEntry(current, nextZipEntry);
            case MAILBOX_ANNOTATION_DIR:
                return next();
            case MAILBOX_ANNOTATION:
                return fromMailboxAnnotationEntry(current, nextZipEntry);
            default:
                return new UnknownArchiveEntry(current.getEntryName());
        }
    }
}
