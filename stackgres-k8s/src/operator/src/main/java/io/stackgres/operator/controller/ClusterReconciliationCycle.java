/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.controller;

import java.util.Optional;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.stackgres.operator.app.KubernetesClientFactory;
import io.stackgres.operator.app.ObjectMapperProvider;
import io.stackgres.operator.cluster.Cluster;
import io.stackgres.operator.common.ArcUtil;
import io.stackgres.operator.common.SidecarEntry;
import io.stackgres.operator.common.StackGresClusterContext;
import io.stackgres.operator.common.StackGresSidecarTransformer;
import io.stackgres.operator.customresource.sgbackup.StackGresBackup;
import io.stackgres.operator.customresource.sgbackup.StackGresBackupDefinition;
import io.stackgres.operator.customresource.sgbackup.StackGresBackupDoneable;
import io.stackgres.operator.customresource.sgbackup.StackGresBackupList;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfig;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigDefinition;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigDoneable;
import io.stackgres.operator.customresource.sgbackupconfig.StackGresBackupConfigList;
import io.stackgres.operator.customresource.sgcluster.StackGresCluster;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterDefinition;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterDoneable;
import io.stackgres.operator.customresource.sgcluster.StackGresClusterList;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigDefinition;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigDoneable;
import io.stackgres.operator.customresource.sgpgconfig.StackGresPostgresConfigList;
import io.stackgres.operator.customresource.sgprofile.StackGresProfile;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileDefinition;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileDoneable;
import io.stackgres.operator.customresource.sgprofile.StackGresProfileList;
import io.stackgres.operator.resource.ClusterSidecarFinder;
import io.stackgres.operator.resource.ResourceUtil;
import io.stackgres.operator.sidecars.envoy.Envoy;
import io.stackgres.operatorframework.reconciliation.AbstractReconciliationCycle;
import io.stackgres.operatorframework.reconciliation.AbstractReconciliator;
import io.stackgres.operatorframework.resource.ResourceHandlerSelector;

import org.jooq.lambda.Unchecked;
import org.jooq.lambda.tuple.Tuple2;

@ApplicationScoped
public class ClusterReconciliationCycle
    extends AbstractReconciliationCycle<StackGresClusterContext> {

  private final ClusterSidecarFinder sidecarFinder;
  private final Cluster cluster;
  private final ClusterStatusManager statusManager;
  private final EventController eventController;

  /**
   * Create a {@code ClusterReconciliationCycle} instance.
   */
  @Inject
  public ClusterReconciliationCycle(KubernetesClientFactory kubClientFactory,
      ClusterSidecarFinder sidecarFinder, Cluster cluster,
      ResourceHandlerSelector<StackGresClusterContext> handlerSelector,
      ClusterStatusManager statusManager, EventController eventController,
      ObjectMapperProvider objectMapperProvider) {
    super("Cluster", kubClientFactory::create, StackGresClusterContext::getCluster,
        handlerSelector, objectMapperProvider.objectMapper());
    this.sidecarFinder = sidecarFinder;
    this.cluster = cluster;
    this.statusManager = statusManager;
    this.eventController = eventController;
  }

  public ClusterReconciliationCycle() {
    super(null, null, c -> null, null, null);
    ArcUtil.checkPublicNoArgsConstructorIsCalledFromArc();
    this.sidecarFinder = null;
    this.cluster = null;
    this.statusManager = null;
    this.eventController = null;
  }

  void onStart(@Observes StartupEvent ev) {
    start();
  }

  void onStop(@Observes ShutdownEvent ev) throws Exception {
    stop();
  }

  @Override
  protected void onError(Exception ex) {
    eventController.sendEvent(EventReason.CLUSTER_CONFIG_ERROR,
        "StackGres Cluster reconciliation cycle failed: "
            + ex.getMessage(), null);
  }

  @Override
  protected void onConfigError(StackGresClusterContext context, HasMetadata configResource,
      Exception ex) {
    eventController.sendEvent(EventReason.CLUSTER_CONFIG_ERROR,
        "StackGres Cluster " + configResource.getMetadata().getNamespace() + "."
            + configResource.getMetadata().getName() + " reconciliation failed: "
            + ex.getMessage(), configResource);
  }

  @Override
  protected AbstractReconciliator<StackGresClusterContext> createReconciliator(
      KubernetesClient client, StackGresClusterContext context,
      ImmutableList<Tuple2<HasMetadata, Optional<HasMetadata>>> requiredResources,
      ImmutableList<Tuple2<HasMetadata, Optional<HasMetadata>>> existingResources) {
    return ClusterReconciliator.builder()
        .withEventController(eventController)
        .withHandlerSelector(handlerSelector)
        .withStatusManager(statusManager)
        .withClient(client)
        .withObjectMapper(objectMapper)
        .withClusterContext(context)
        .withRequiredResources(requiredResources)
        .withExistingResources(existingResources)
        .build();
  }

  @Override
  protected ImmutableList<HasMetadata> getRequiredResources(StackGresClusterContext context,
      ImmutableList<HasMetadata> existingResourcesOnly) {
    return cluster.getResources(
        ImmutableResourceGeneratorContext.<StackGresClusterContext>builder()
        .context(context)
        .addAllExistingResources(existingResourcesOnly)
        .build());
  }

  @Override
  protected void onOrphanConfigDeletion(String namespace, String name) {
    eventController.sendEvent(EventReason.CLUSTER_DELETED,
        "StackGres Cluster " + namespace + "."
            + name + " deleted");
  }

  @Override
  protected ImmutableList<StackGresClusterContext> getExistingConfigs(KubernetesClient client) {
    return ResourceUtil.getCustomResource(client, StackGresClusterDefinition.NAME)
        .map(crd -> client
            .customResources(crd,
                StackGresCluster.class,
                StackGresClusterList.class,
                StackGresClusterDoneable.class)
            .inAnyNamespace()
            .list()
            .getItems()
            .stream()
            .map(cluster -> getClusterConfig(cluster, client))
            .collect(ImmutableList.toImmutableList()))
        .orElseThrow(() -> new IllegalStateException("StackGres is not correctly installed:"
            + " CRD " + StackGresClusterDefinition.NAME + " not found."));
  }

  private StackGresClusterContext getClusterConfig(StackGresCluster cluster,
      KubernetesClient client) {
    return StackGresClusterContext.builder()
        .withCluster(cluster)
        .withProfile(getProfile(cluster, client))
        .withPostgresConfig(getPostgresConfig(cluster, client))
        .withBackupConfig(getBackupConfig(cluster, client))
        .withSidecars(Stream.of(
            Stream.of(Envoy.NAME)
            .filter(envoy -> !cluster.getSpec().getSidecars().contains(envoy)),
            cluster.getSpec().getSidecars().stream())
            .flatMap(s -> s)
            .map(sidecar -> sidecarFinder.getSidecarTransformer(sidecar))
            .map(Unchecked.function(sidecar -> getSidecarEntry(cluster, client, sidecar)))
            .collect(ImmutableList.toImmutableList()))
        .withBackups(getBackups(cluster, client))
        .build();
  }

  private <T extends CustomResource> SidecarEntry<T, StackGresClusterContext> getSidecarEntry(
      StackGresCluster cluster, KubernetesClient client,
      StackGresSidecarTransformer<T, StackGresClusterContext> sidecar) throws Exception {
    Optional<T> sidecarConfig = sidecar.getConfig(cluster, client);
    return new SidecarEntry<T, StackGresClusterContext>(sidecar, sidecarConfig);
  }

  private Optional<StackGresPostgresConfig> getPostgresConfig(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String pgConfig = cluster.getSpec().getPostgresConfig();
    if (pgConfig != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresPostgresConfigDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresPostgresConfig.class,
                StackGresPostgresConfigList.class,
                StackGresPostgresConfigDoneable.class)
            .inNamespace(namespace)
            .withName(pgConfig)
            .get());
      }
    }
    return Optional.empty();
  }

  private Optional<StackGresBackupConfig> getBackupConfig(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String backupConfig = cluster.getSpec().getBackupConfig();
    if (backupConfig != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresBackupConfigDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresBackupConfig.class,
                StackGresBackupConfigList.class,
                StackGresBackupConfigDoneable.class)
            .inNamespace(namespace)
            .withName(backupConfig)
            .get());
      }
    }
    return Optional.empty();
  }

  private Optional<StackGresProfile> getProfile(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String profileName = cluster.getSpec().getResourceProfile();
    if (profileName != null) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresProfileDefinition.NAME);
      if (crd.isPresent()) {
        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresProfile.class,
                StackGresProfileList.class,
                StackGresProfileDoneable.class)
            .inNamespace(namespace)
            .withName(profileName)
            .get());
      }
    }
    return Optional.empty();
  }

  private ImmutableList<StackGresBackup> getBackups(StackGresCluster cluster,
      KubernetesClient client) {
    final String namespace = cluster.getMetadata().getNamespace();
    final String name = cluster.getMetadata().getName();
    return ResourceUtil.getCustomResource(client, StackGresBackupDefinition.NAME)
        .map(crd -> client
            .customResources(crd,
                StackGresBackup.class,
                StackGresBackupList.class,
                StackGresBackupDoneable.class)
            .inNamespace(namespace)
            .list()
            .getItems()
            .stream()
            .filter(backup -> backup.getSpec().getCluster().equals(name))
            .collect(ImmutableList.toImmutableList()))
        .orElse(ImmutableList.of());
  }

}
