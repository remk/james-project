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
package org.apache.james.mailbox.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.backup.ZipAssert.EntryChecks;
import org.apache.james.mailbox.backup.zip.ZipArchivesLoader;
import org.apache.james.mailbox.backup.zip.Zipper;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import reactor.core.publisher.Mono;

class DefaultMailboxBackupTest implements MailboxMessageFixture {
    private static final int BUFFER_SIZE = 4096;

    private final ArchiveService archiveService = new Zipper();
    private MailArchivesLoader archiveLoader;

    private MailArchiveRestorer archiveRestorer;
    private MailboxManager mailboxManager;
    private DefaultMailboxBackup backup;

    private MailboxSession sessionUser;
    private MailboxSession sessionOtherUser;

    @BeforeEach
    void beforeEach() throws Exception {
        prepareForTest(ZipArchivesLoader.DEFAULT_FILE_THRESHOLD);
    }

    void prepareForTestWithContentInTempFile() throws Exception {
        int smallFileThreshold = 1;
        prepareForTest(smallFileThreshold);
    }

    private void prepareForTest(int zipLoaderFileThreshold) throws MailboxException {
        archiveLoader = new ZipArchivesLoader(zipLoaderFileThreshold);
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        archiveRestorer = new ZipMailArchiveRestorer(mailboxManager, archiveLoader);
        backup = new DefaultMailboxBackup(mailboxManager, archiveService, archiveRestorer);
        sessionUser = mailboxManager.createSystemSession(USER);
        sessionOtherUser = mailboxManager.createSystemSession(OTHER_USER);
    }

    private void createMailBoxWithMessages(MailboxSession session, MailboxPath mailboxPath, MessageManager.AppendCommand... messages) throws Exception {
        MailboxId mailboxId = mailboxManager.createMailbox(mailboxPath, session).get();
        Arrays.stream(messages).forEach(Throwing.consumer(message ->
                mailboxManager.getMailbox(mailboxId, session).appendMessage(message, session)
            )
        );
    }

    private void createMailBox(MailboxSession session, MailboxPath mailboxPath) throws Exception {
        createMailBoxWithMessages(session, mailboxPath);
    }

    @Test
    void doBackupWithoutMailboxShouldStoreEmptyBackup() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching();
        }
    }

    @Test
    void doBackupWithoutMessageShouldStoreAnArchiveWithOnlyOneEntry() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);

        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory());
        }
    }

    @Test
    void doBackupMailboxWithAnnotationShouldStoreAnArchiveWithMailboxAndAnnotation() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        mailboxManager.updateAnnotations(MAILBOX_PATH_USER1_MAILBOX1, sessionUser, WITH_ANNOTATION_1);

        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_1_NAME + "/" + Zipper.ANNOTATION_DIRECTORY + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_1_NAME + "/" + Zipper.ANNOTATION_DIRECTORY + "/" + ANNOTATION_1_KEY.asString()).hasStringContent(ANNOTATION_1_CONTENT)
            );
        }
    }

    @Test
    void doBackupWithOneMessageShouldStoreAnArchiveWithTwoEntries() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailBoxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());

        backup.backupAccount(USER1, destination);

        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void doBackupWithTwoMailboxesAndOneMessageShouldStoreAnArchiveWithThreeEntries() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        createMailBoxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX2);

        backup.backupAccount(USER1, destination);

        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MAILBOX_2_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void doBackupShouldOnlyArchiveTheMailboxOfTheUser() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);

        createMailBoxWithMessages(sessionUser, MAILBOX_PATH_USER1_MAILBOX1, getMessage1AppendCommand());
        createMailBoxWithMessages(sessionOtherUser, MAILBOX_PATH_OTHER_USER_MAILBOX1, getMessage1OtherUserAppendCommand());

        backup.backupAccount(USER1, destination);
        try (ZipAssert zipAssert = ZipAssert.assertThatZip(destination)) {
            zipAssert.containsOnlyEntriesMatching(
                EntryChecks.hasName(MAILBOX_1_NAME + "/").isDirectory(),
                EntryChecks.hasName(MESSAGE_ID_1.serialize()).hasStringContent(MESSAGE_CONTENT_1)
            );
        }
    }

    @Test
    void backupEmptyAccountThenRestoringItInUser2AccountShouldCreateNoElements() throws Exception {
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        Mono.from(backup.restore(USER2, source)).block();

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).isEmpty();
    }

    @Test
    void backupAccountWithOneMailboxThenRestoringItInUser2AccountShouldCreateOneMailbox() throws Exception {
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        Mono.from(backup.restore(USER2, source)).block();

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(1);
        DefaultMailboxBackup.MailAccountContent mailAccountContent = content.get(0);
        Mailbox mailbox = mailAccountContent.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox.getName()).isEqualTo(MAILBOX_1_NAME);
        assertThat(mailAccountContent.getMessages().count()).isEqualTo(0);
    }

    @Test
    void backupAccountWithTwoMailboxThenRestoringItInUser2AccountShouldCreateTwoMailbox() throws Exception {
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX2);

        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        Mono.from(backup.restore(USER2, source)).block();

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(2);
        DefaultMailboxBackup.MailAccountContent contentMailbox1 = content.get(0);
        Mailbox mailbox1 = contentMailbox1.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox1.getName()).isEqualTo(MAILBOX_1_NAME);
        assertThat(contentMailbox1.getMessages().count()).isEqualTo(0);

        DefaultMailboxBackup.MailAccountContent contentMailbox2 = content.get(1);
        Mailbox mailbox2 = contentMailbox2.getMailboxWithAnnotations().mailbox;
        assertThat(mailbox2.getName()).isEqualTo(MAILBOX_2_NAME);
        assertThat(contentMailbox2.getMessages().count()).isEqualTo(0);

    }


    private void backupAccountWithOneMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements() throws Exception {
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        mailboxManager.updateAnnotations(MAILBOX_PATH_USER1_MAILBOX1, sessionUser, WITH_ANNOTATION_1_AND_2);
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        Mono.from(backup.restore(USER2, source)).block();

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(1);
        DefaultMailboxBackup.MailAccountContent mailAccountContent = content.get(0);
        MailboxWithAnnotations mailboxWithAnnotations = mailAccountContent.getMailboxWithAnnotations();
        Mailbox mailbox = mailboxWithAnnotations.mailbox;
        List<MailboxAnnotation> annotations = mailboxWithAnnotations.annotations;
        assertThat(mailbox.getName()).isEqualTo(MAILBOX_1_NAME);
        assertThat(annotations).isEqualTo(WITH_ANNOTATION_1_AND_2);
    }

    @Test
    void backupAccountWithOneMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElementsInMemory() throws Exception {
        backupAccountWithOneMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements();
    }

    @Test
    void backupAccountWithOneMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElementsInFile() throws Exception {
        prepareForTestWithContentInTempFile();
        backupAccountWithOneMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements();
    }

    private void backupAccountWithTwoMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements() throws Exception {
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX1);
        createMailBox(sessionUser, MAILBOX_PATH_USER1_MAILBOX2);
        mailboxManager.updateAnnotations(MAILBOX_PATH_USER1_MAILBOX1, sessionUser, WITH_ANNOTATION_1_AND_2);
        mailboxManager.updateAnnotations(MAILBOX_PATH_USER1_MAILBOX2, sessionUser, WITH_ANNOTATION_1);
        ByteArrayOutputStream destination = new ByteArrayOutputStream(BUFFER_SIZE);
        backup.backupAccount(USER1, destination);

        InputStream source = new ByteArrayInputStream(destination.toByteArray());
        Mono.from(backup.restore(USER2, source)).block();

        List<DefaultMailboxBackup.MailAccountContent> content = backup.getAccountContentForUser(sessionOtherUser);

        assertThat(content).hasSize(2);
        {
            DefaultMailboxBackup.MailAccountContent mailAccountContent = content.get(0);
            MailboxWithAnnotations mailboxWithAnnotations = mailAccountContent.getMailboxWithAnnotations();
            Mailbox mailbox = mailboxWithAnnotations.mailbox;
            List<MailboxAnnotation> annotations = mailboxWithAnnotations.annotations;
            assertThat(mailbox.getName()).isEqualTo(MAILBOX_1_NAME);
            assertThat(annotations).isEqualTo(WITH_ANNOTATION_1_AND_2);
        }
        {
            DefaultMailboxBackup.MailAccountContent mailAccountContent = content.get(1);
            MailboxWithAnnotations mailboxWithAnnotations = mailAccountContent.getMailboxWithAnnotations();
            Mailbox mailbox = mailboxWithAnnotations.mailbox;
            List<MailboxAnnotation> annotations = mailboxWithAnnotations.annotations;
            assertThat(mailbox.getName()).isEqualTo(MAILBOX_2_NAME);
            assertThat(annotations).isEqualTo(WITH_ANNOTATION_1);
        }
    }

    @Test
    void backupAccountWithTwoMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElementsInMemory() throws Exception {
        backupAccountWithTwoMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements();
    }

    @Test
    void backupAccountWithTwoMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElementsInFile() throws Exception {
        prepareForTestWithContentInTempFile();
        backupAccountWithTwoMailboxWithAnnotationsThenRestoringItInUser2AccountShouldRestoreAllElements();
    }


    private MessageManager.AppendCommand getMessage1AppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1.getFullContent());
    }

    private MessageManager.AppendCommand getMessage2AppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_2.getFullContent());
    }

    private MessageManager.AppendCommand getMessage1OtherUserAppendCommand() throws IOException {
        return MessageManager.AppendCommand.builder().withFlags(flags1).build(MESSAGE_1_OTHER_USER.getFullContent());
    }

}
