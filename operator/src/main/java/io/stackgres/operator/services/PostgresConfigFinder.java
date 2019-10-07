/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.services;

import java.util.Optional;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.common.customresource.sgpgconfig.StackGresPostgresConfig;
import io.stackgres.common.customresource.sgpgconfig.StackGresPostgresConfigDefinition;
import io.stackgres.common.customresource.sgpgconfig.StackGresPostgresConfigDoneable;
import io.stackgres.common.customresource.sgpgconfig.StackGresPostgresConfigList;
import io.stackgres.common.resource.ResourceUtil;
import io.stackgres.operator.app.KubernetesClientFactory;

@ApplicationScoped
public class PostgresConfigFinder
    implements KubernetesCustomResourceFinder<StackGresPostgresConfig> {

  private KubernetesClientFactory kubClientFactory;

  @Inject
  public PostgresConfigFinder(KubernetesClientFactory kubClientFactory) {
    this.kubClientFactory = kubClientFactory;
  }

  @Override
  public Optional<StackGresPostgresConfig> findByNameAndNamespace(String name, String namespace) {

    try (KubernetesClient client = kubClientFactory.create()) {
      Optional<CustomResourceDefinition> crd =
          ResourceUtil.getCustomResource(client, StackGresPostgresConfigDefinition.NAME);
      if (crd.isPresent()) {

        return Optional.ofNullable(client
            .customResources(crd.get(),
                StackGresPostgresConfig.class,
                StackGresPostgresConfigList.class,
                StackGresPostgresConfigDoneable.class)
            .inNamespace(namespace)
            .withName(name)
            .get());
      }
    }
    return Optional.empty();
  }

}
