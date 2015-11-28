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
package org.apache.syncope.client.console.panels;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import org.apache.commons.collections4.IterableUtils;
import org.apache.commons.collections4.Predicate;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.syncope.client.console.commons.Constants;
import org.apache.syncope.client.console.pages.AbstractBasePage;
import org.apache.syncope.client.console.rest.ConnectorRestClient;
import org.apache.syncope.client.console.rest.ResourceRestClient;
import org.apache.syncope.client.console.topology.TopologyNode;
import org.apache.syncope.client.console.wicket.markup.html.bootstrap.dialog.BaseModal;
import org.apache.syncope.client.console.wicket.markup.html.form.ActionLink;
import org.apache.syncope.client.console.wizards.AjaxWizard;
import org.apache.syncope.client.console.wizards.provision.ProvisionWizardBuilder;
import org.apache.syncope.common.lib.to.MappingItemTO;
import org.apache.syncope.common.lib.to.ProvisionTO;
import org.apache.syncope.common.lib.to.ResourceTO;
import org.apache.syncope.common.lib.types.StandardEntitlement;
import org.apache.wicket.PageReference;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.authroles.authorization.strategies.role.metadata.MetaDataRoleAuthorizationStrategy;
import org.apache.wicket.event.Broadcast;
import org.apache.wicket.extensions.markup.html.tabs.AbstractTab;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.ResourceModel;

/**
 * Modal window with Resource form.
 */
public class ResourceModal extends AbstractResourceModal {

    private static final long serialVersionUID = 1734415311027284221L;

    private final ResourceRestClient resourceRestClient = new ResourceRestClient();

    private final ConnectorRestClient connectorRestClient = new ConnectorRestClient();

    private final boolean createFlag;

    public ResourceModal(
            final BaseModal<Serializable> modal,
            final PageReference pageRef,
            final IModel<ResourceTO> model,
            final boolean createFlag) {

        super(modal, pageRef);

        this.createFlag = createFlag;

        //--------------------------------
        // Resource details panel
        //--------------------------------
        tabs.add(new AbstractTab(new ResourceModel("general", "general")) {

            private static final long serialVersionUID = -5861786415855103549L;

            @Override
            public Panel getPanel(final String panelId) {
                return new ResourceDetailsPanel(panelId, model,
                        resourceRestClient.getPropagationActionsClasses(), createFlag);
            }
        });
        //--------------------------------

        //--------------------------------
        // Resource provision panels
        //--------------------------------
        final ListViewPanel.Builder<ProvisionTO> builder = new ListViewPanel.Builder<>(ProvisionTO.class, pageRef);
        builder.setItems(model.getObject().getProvisions());
        builder.includes("anyType", "objectClass");

        builder.addAction(new ActionLink<ProvisionTO>() {

            private static final long serialVersionUID = -3722207913631435504L;

            @Override
            public void onClick(final AjaxRequestTarget target, final ProvisionTO provisionTO) {
                send(pageRef.getPage(), Broadcast.DEPTH,
                        new AjaxWizard.NewItemActionEvent<>(provisionTO, 2, target));
            }
        }, ActionLink.ActionType.MAPPING, StandardEntitlement.RESOURCE_UPDATE).addAction(new ActionLink<ProvisionTO>() {

            private static final long serialVersionUID = -3722207913631435514L;

            @Override
            public void onClick(final AjaxRequestTarget target, final ProvisionTO provisionTO) {
                send(pageRef.getPage(), Broadcast.DEPTH,
                        new AjaxWizard.NewItemActionEvent<>(provisionTO, 3, target));
            }
        }, ActionLink.ActionType.ACCOUNT_LINK, StandardEntitlement.RESOURCE_UPDATE).addAction(
                new ActionLink<ProvisionTO>() {

            private static final long serialVersionUID = -3722207913631435524L;

            @Override
            public void onClick(final AjaxRequestTarget target, final ProvisionTO provisionTO) {
                provisionTO.setSyncToken(null);
                send(pageRef.getPage(), Broadcast.DEPTH,
                        new AjaxWizard.NewItemFinishEvent<>(provisionTO, target));
            }
        }, ActionLink.ActionType.RESET_TIME, StandardEntitlement.RESOURCE_UPDATE).addAction(
                        new ActionLink<ProvisionTO>() {

                    private static final long serialVersionUID = -3722207913631435534L;

                    @Override
                    public void onClick(final AjaxRequestTarget target, final ProvisionTO provisionTO) {
                        send(pageRef.getPage(), Broadcast.DEPTH,
                                new AjaxWizard.NewItemActionEvent<>(SerializationUtils.clone(provisionTO), target));
                    }
                }, ActionLink.ActionType.CLONE, StandardEntitlement.RESOURCE_CREATE).addAction(
                        new ActionLink<ProvisionTO>() {

                    private static final long serialVersionUID = -3722207913631435544L;

                    @Override
                    public void onClick(final AjaxRequestTarget target, final ProvisionTO provisionTO) {
                        model.getObject().getProvisions().remove(provisionTO);
                        send(pageRef.getPage(), Broadcast.DEPTH,
                                new AjaxWizard.NewItemFinishEvent<ProvisionTO>(null, target));
                    }
                }, ActionLink.ActionType.DELETE, StandardEntitlement.RESOURCE_DELETE);

        builder.addNewItemPanelBuilder(new ProvisionWizardBuilder("wizard", model.getObject(), pageRef));
        builder.addNotificationPanel(modal.getFeedbackPanel());

        tabs.add(new AbstractTab(new ResourceModel("provisions", "provisions")) {

            private static final long serialVersionUID = -5861786415855103549L;

            @Override
            public Panel getPanel(final String panelId) {
                return builder.build(panelId);
            }
        });
        //--------------------------------

        //--------------------------------
        // Resource connector configuration panel
        //--------------------------------
        tabs.add(new AbstractTab(new ResourceModel("connectorProperties", "connectorProperties")) {

            private static final long serialVersionUID = -5861786415855103549L;

            @Override
            public Panel getPanel(final String panelId) {
                final ResourceConnConfPanel panel = new ResourceConnConfPanel(panelId, model, createFlag) {

                    private static final long serialVersionUID = 1L;

                    @Override
                    protected void check(final AjaxRequestTarget target) {
                        if (connectorRestClient.check(model.getObject())) {
                            info(getString("success_connection"));
                        } else {
                            error(getString("error_connection"));
                        }
                        modal.getFeedbackPanel().refresh(target);
                    }
                };
                MetaDataRoleAuthorizationStrategy.authorize(panel, ENABLE, StandardEntitlement.CONNECTOR_READ);
                return panel;
            }
        });
        //--------------------------------

        //--------------------------------
        // Resource security panel
        //--------------------------------
        tabs.add(new AbstractTab(new ResourceModel("security", "security")) {

            private static final long serialVersionUID = -5861786415855103549L;

            @Override
            public Panel getPanel(final String panelId) {
                return new ResourceSecurityPanel(panelId, model);
            }
        });
        //--------------------------------
    }

    @Override
    public void onError(final AjaxRequestTarget target, final Form<?> form) {
        modal.getFeedbackPanel().refresh(target);
    }

    @Override
    public void onSubmit(final AjaxRequestTarget target, final Form<?> form) {
        final ResourceTO resourceTO = (ResourceTO) form.getDefaultModelObject();

        boolean connObjectKeyError = false;

        final Collection<ProvisionTO> provisions = new ArrayList<>(resourceTO.getProvisions());

        for (ProvisionTO provision : provisions) {
            if (provision != null) {
                if (provision.getMapping() == null || provision.getMapping().getItems().isEmpty()) {
                    resourceTO.getProvisions().remove(provision);
                } else {
                    long uConnObjectKeyCount = IterableUtils.countMatches(
                            provision.getMapping().getItems(), new Predicate<MappingItemTO>() {

                        @Override
                        public boolean evaluate(final MappingItemTO item) {
                            return item.isConnObjectKey();
                        }
                    });

                    connObjectKeyError = uConnObjectKeyCount != 1;
                }
            }
        }

        if (connObjectKeyError) {
            error(getString("connObjectKeyValidation"));
            modal.getFeedbackPanel().refresh(target);
        } else {
            try {
                if (createFlag) {
                    final ResourceTO actual = resourceRestClient.create(resourceTO);
                    send(pageRef.getPage(), Broadcast.BREADTH, new CreateEvent(
                            actual.getKey(),
                            actual.getKey(),
                            TopologyNode.Kind.RESOURCE,
                            actual.getConnector(),
                            target));
                } else {
                    resourceRestClient.update(resourceTO);
                }

                if (pageRef.getPage() instanceof AbstractBasePage) {
                    ((AbstractBasePage) pageRef.getPage()).setModalResult(true);
                }
                modal.close(target);
            } catch (Exception e) {
                LOG.error("Failure managing resource {}", resourceTO, e);
                error(getString(Constants.ERROR) + ": " + e.getMessage());
                modal.getFeedbackPanel().refresh(target);
            }
        }
    }

}
