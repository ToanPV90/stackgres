/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.resource;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.google.common.collect.ImmutableList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.stackgres.apiweb.dto.secret.SecretDto;
import io.stackgres.apiweb.transformer.SecretMapper;
import io.stackgres.common.KubernetesClientFactory;
import io.stackgres.common.resource.ResourceScanner;

@ApplicationScoped
public class SecretDtoScanner implements ResourceScanner<SecretDto> {

  private final KubernetesClientFactory factory;

  @Inject
  public SecretDtoScanner(KubernetesClientFactory factory) {
    this.factory = factory;
  }

  @Override
  public List<SecretDto> findResources() {
    try (KubernetesClient client = factory.create()) {
      return client.secrets().list().getItems().stream()
          .map(SecretMapper::map)
          .collect(ImmutableList.toImmutableList());
    }
  }

  @Override
  public List<SecretDto> findResourcesInNamespace(String namespace) {
    try (KubernetesClient client = factory.create()) {
      return client.secrets().inNamespace(namespace).list().getItems().stream()
          .map(SecretMapper::map)
          .collect(ImmutableList.toImmutableList());
    }
  }
}
