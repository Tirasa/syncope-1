/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.core.provisioning.java.propagation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.syncope.common.lib.to.PropagationStatus;
import org.apache.syncope.common.lib.types.PropagationTaskExecStatus;
import org.apache.syncope.core.persistence.api.entity.task.PropagationTask;
import org.apache.syncope.core.provisioning.api.propagation.PropagationReporter;
import org.apache.syncope.core.provisioning.api.propagation.PropagationTaskInfo;
import org.apache.syncope.core.provisioning.java.utils.ConnObjectUtils;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultPropagationReporter implements PropagationReporter {

    protected static final Logger LOG = LoggerFactory.getLogger(DefaultPropagationReporter.class);

    protected final List<PropagationStatus> statuses = new ArrayList<>();

    protected boolean add(final PropagationStatus status) {
        return IterableUtils.matchesAny(statuses, new Predicate<PropagationStatus>() {

            @Override
            public boolean evaluate(final PropagationStatus item) {
                return item.getResource().equals(status.getResource());
            }
        })
                ? false
                : statuses.add(status);
    }

    @Override
    public void onSuccessOrNonPriorityResourceFailures(
            final PropagationTaskInfo taskInfo,
            final PropagationTaskExecStatus executionStatus,
            final String failureReason,
            final ConnectorObject beforeObj,
            final ConnectorObject afterObj) {

        PropagationStatus status = new PropagationStatus();
        status.setResource(taskInfo.getResource());
        status.setStatus(executionStatus);
        status.setFailureReason(failureReason);

        if (beforeObj != null) {
            status.setBeforeObj(ConnObjectUtils.getConnObjectTO(beforeObj));
        }

        if (afterObj != null) {
            status.setAfterObj(ConnObjectUtils.getConnObjectTO(afterObj));
        }

        add(status);
    }

    @Override
    public void onPriorityResourceFailure(
            final String failingResource,
            final Collection<PropagationTaskInfo> taskInfos) {

        LOG.debug("Propagation error: {} priority resource failed to propagate", failingResource);

        final PropagationTaskInfo taskInfo = IterableUtils.find(taskInfos, new Predicate<PropagationTaskInfo>() {

            @Override
            public boolean evaluate(final PropagationTaskInfo taskInfo) {
                return taskInfo.getResource().equals(failingResource);
            }
        });

        if (taskInfo == null) {
            LOG.error("Could not find {} for {}", PropagationTask.class.getName(), failingResource);
        } else {
            PropagationStatus status = new PropagationStatus();
            status.setResource(taskInfo.getResource());
            status.setStatus(PropagationTaskExecStatus.FAILURE);
            status.setFailureReason(
                    "Propagation error: " + failingResource + " priority resource failed to propagate.");
            add(status);
        }
    }

    @Override
    public List<PropagationStatus> getStatuses() {
        return Collections.unmodifiableList(statuses);
    }
}
