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

package org.apache.james.blob.memory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.inject.Inject;

import org.apache.commons.io.IOUtils;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.DeduplicatingBlobStore;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Mono;

public class MemoryDeduplicatingBlobStore implements DeduplicatingBlobStore {
    private final BlobId.Factory factory;
    private final BucketName defaultBucketName;
    private final MemoryBlobStore blobStore;

    @Inject
    public MemoryDeduplicatingBlobStore(BlobId.Factory factory, MemoryBlobStore blobStore) {
        this(factory, BucketName.DEFAULT, blobStore);
    }

    @VisibleForTesting
    public MemoryDeduplicatingBlobStore(BlobId.Factory factory, BucketName defaultBucketName, MemoryBlobStore blobStore) {
        this.factory = factory;
        this.defaultBucketName = defaultBucketName;
        this.blobStore = blobStore;
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, byte[] data, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(data);

        BlobId blobId = factory.forPayload(data);

        return blobStore.save(bucketName, blobId, data)
            .then(Mono.just(blobId));
    }

    @Override
    public Mono<BlobId> save(BucketName bucketName, InputStream data, StoragePolicy storagePolicy) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(data);
        try {
            byte[] bytes = IOUtils.toByteArray(data);
            return save(bucketName, bytes, storagePolicy);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Mono<byte[]> readBytes(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        return retrieveStoredValue(bucketName, blobId);
    }

    @Override
    public InputStream read(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        return retrieveStoredValue(bucketName, blobId)
            .map(ByteArrayInputStream::new)
            .block();
    }

    @Override
    public Mono<Void> deleteBucket(BucketName bucketName) {
        Preconditions.checkNotNull(bucketName);

        return blobStore.deleteBucket(bucketName);
    }

    private Mono<byte[]> retrieveStoredValue(BucketName bucketName, BlobId blobId) {
        return blobStore.readBytes(bucketName, blobId);
    }

    @Override
    public BucketName getDefaultBucketName() {
        return defaultBucketName;
    }

    @Override
    public Mono<Void> delete(BucketName bucketName, BlobId blobId) {
        Preconditions.checkNotNull(bucketName);
        Preconditions.checkNotNull(blobId);

        return blobStore.delete(bucketName, blobId);
    }

}
