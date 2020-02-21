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

package org.apache.james.blob.api;

import static org.apache.james.blob.api.DeduplicatingBlobStore.StoragePolicy.LOW_COST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.commons.io.IOUtils;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.junit.jupiter.api.Test;

import com.google.common.base.Strings;

public interface DeleteDeduplicatingBlobStoreContract {

    String SHORT_STRING = "toto";
    byte[] SHORT_BYTEARRAY = SHORT_STRING.getBytes(StandardCharsets.UTF_8);
    byte[] ELEVEN_KILOBYTES = Strings.repeat("0123456789\n", 1000).getBytes(StandardCharsets.UTF_8);
    String TWELVE_MEGABYTES_STRING = Strings.repeat("0123456789\r\n", 1024 * 1024);
    byte[] TWELVE_MEGABYTES = TWELVE_MEGABYTES_STRING.getBytes(StandardCharsets.UTF_8);
    BucketName CUSTOM = BucketName.of("custom");

    DeduplicatingBlobStore testee();

    BlobId.Factory blobIdFactory();

    @Test
    default void deleteShouldNotThrowWhenBlobDoesNotExist() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        assertThatCode(() -> store.delete(defaultBucketName, blobIdFactory().randomId()).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldDeleteExistingBlobData() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();
        store.delete(defaultBucketName, blobId).block();

        assertThatThrownBy(() -> store.read(defaultBucketName, blobId).read())
            .isInstanceOf(ObjectStoreException.class);
    }

    @Test
    default void deleteShouldBeIdempotent() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();
        store.delete(defaultBucketName, blobId).block();

        assertThatCode(() -> store.delete(defaultBucketName, blobId).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void deleteShouldNotDeleteOtherBlobs() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobIdToDelete = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();
        BlobId otherBlobId = store.save(defaultBucketName, ELEVEN_KILOBYTES, LOW_COST).block();

        store.delete(defaultBucketName, blobIdToDelete).block();

        InputStream read = store.read(defaultBucketName, otherBlobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(ELEVEN_KILOBYTES));
    }

    @Test
    default void deleteConcurrentlyShouldNotFail() throws Exception {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, TWELVE_MEGABYTES, LOW_COST).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> store.delete(defaultBucketName, blobId).block()))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(1));
    }

    @Test
    default void deleteShouldThrowWhenNullBucketName() {
        DeduplicatingBlobStore store = testee();
        assertThatThrownBy(() -> store.delete(null, blobIdFactory().randomId()).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucket() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId customBlobId = store.save(CUSTOM, "custom_string", LOW_COST).block();
        BlobId defaultBlobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();

        store.delete(CUSTOM, customBlobId).block();

        InputStream read = store.read(defaultBucketName, defaultBlobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void deleteShouldNotDeleteFromOtherBucketWhenSameBlobId() {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        store.save(CUSTOM, SHORT_BYTEARRAY, LOW_COST).block();
        BlobId blobId = store.save(defaultBucketName, SHORT_BYTEARRAY, LOW_COST).block();

        store.delete(defaultBucketName, blobId).block();

        InputStream read = store.read(CUSTOM, blobId);

        assertThat(read).hasSameContentAs(new ByteArrayInputStream(SHORT_BYTEARRAY));
    }

    @Test
    default void readShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, TWELVE_MEGABYTES, LOW_COST).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    InputStream read = store.read(defaultBucketName, blobId);

                    String string = IOUtils.toString(read, StandardCharsets.UTF_8);
                    if (!string.equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it. Size : " + string.length());
                    }
                } catch (ObjectStoreException exception) {
                    // normal behavior here
                }

                store.delete(defaultBucketName, blobId).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }

    @Test
    default void readBytesShouldNotReadPartiallyWhenDeletingConcurrentlyBigBlob() throws Exception {
        DeduplicatingBlobStore store = testee();
        BucketName defaultBucketName = store.getDefaultBucketName();

        BlobId blobId = store.save(defaultBucketName, TWELVE_MEGABYTES, LOW_COST).block();

        ConcurrentTestRunner.builder()
            .operation(((threadNumber, step) -> {
                try {
                    byte[] read = store.readBytes(defaultBucketName, blobId).block();
                    String string = IOUtils.toString(read, StandardCharsets.UTF_8.displayName());
                    if (!string.equals(TWELVE_MEGABYTES_STRING)) {
                        throw new RuntimeException("Should not read partial blob when an other thread is deleting it. Size : " + string.length());
                    }
                } catch (ObjectStoreException exception) {
                    // normal behavior here
                }

                store.delete(defaultBucketName, blobId).block();
            }))
            .threadCount(10)
            .operationCount(10)
            .runSuccessfullyWithin(Duration.ofMinutes(3));
    }
}
