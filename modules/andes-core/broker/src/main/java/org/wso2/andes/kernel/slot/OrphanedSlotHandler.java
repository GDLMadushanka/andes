/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.andes.kernel.slot;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.andes.kernel.*;
import org.wso2.andes.server.cluster.coordination.hazelcast.HazelcastAgent;
import org.wso2.andes.thrift.MBThriftClient;
import org.wso2.andes.subscription.SubscriptionStore;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * This class will reassign the slots own by this node when its last subscriber leaves
 */
public class OrphanedSlotHandler implements SubscriptionListener {

    private static Log log = LogFactory
            .getLog(OrphanedSlotHandler.class);

    @Override
    public void handleClusterSubscriptionsChanged(AndesSubscription subscription,
                                                  SubscriptionChange changeType)
            throws AndesException {

        //Cluster wise changes are not necessary
    }

    @Override
    public void handleLocalSubscriptionsChanged(LocalSubscription subscription,
                                                SubscriptionChange changeType)
            throws AndesException {
        switch (changeType) {
            case DELETED:
                reAssignSlotsIfNeeded(subscription);
                break;
            case DISCONNECTED:
                reAssignSlotsIfNeeded(subscription);
                break;
        }
    }


    /**
     * Re-assign slots back to the slot manager if this is the last subscriber of this node.
     *
     * @param subscription current subscription fo the leaving node
     * @throws AndesException
     */
    private void reAssignSlotsIfNeeded(AndesSubscription subscription) throws AndesException {
        if (subscription.isDurable()) {
            // Problem happens only with Queues
            SubscriptionStore subscriptionStore = AndesContext
                    .getInstance().getSubscriptionStore();
            String destination = subscription
                    .getSubscribedDestination();
            Collection<LocalSubscription> localSubscribersForQueue = subscriptionStore
                    .getActiveLocalSubscribers(destination, false);
            if (localSubscribersForQueue.size() == 0) {
                String nodeId = HazelcastAgent.getInstance().getNodeId();
                try {
                    MBThriftClient.reAssignSlotWhenNoSubscribers(nodeId,
                            subscription.getTargetQueue());
                    //remove tracking when orphan slot situation
                    ConcurrentMap<String, Set<Slot>> subscriptionSlotTracker = OnflightMessageTracker.getInstance()
                            .getSubscriptionSlotTracker();
                    Set<Slot> slotsToRemoveTrackingIfOrphaned = subscriptionSlotTracker
                            .get(subscription.getTargetQueue());
                    for (Slot slot : slotsToRemoveTrackingIfOrphaned) {
                        OnflightMessageTracker.getInstance().clearAllTrackingWhenOrphanedSlotSituation(slot);
                    }

                } catch (ConnectionException e) {
                    log.error("Error occurred while re-assigning the slot to slot manager", e);
                    throw new AndesException(
                            "Error occurred while re-assigning the slot to slot manager", e);
                }
            }

        }
    }

}
