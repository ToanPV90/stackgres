/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.apiweb.rest;

import java.util.List;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Path;

import io.quarkus.security.Authenticated;
import io.stackgres.apiweb.dto.shardedbackup.ShardedBackupDto;
import io.stackgres.common.crd.sgshardedbackup.StackGresShardedBackup;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@Path("sgshardedbackups")
@RequestScoped
@Authenticated
public class ShardedBackupResource
    extends AbstractRestService<ShardedBackupDto, StackGresShardedBackup> {

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK",
              content = { @Content(
                  mediaType = "application/json",
                  array = @ArraySchema(
                      schema = @Schema(implementation = ShardedBackupDto.class))) })
      })
  @Override
  public List<ShardedBackupDto> list() {
    return super.list();
  }

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK")
      })
  @Override
  public void create(ShardedBackupDto resource) {
    super.create(resource);
  }

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK")
      })
  @Override
  public void delete(ShardedBackupDto resource) {
    super.delete(resource);
  }

  @Operation(
      responses = {
          @ApiResponse(responseCode = "200", description = "OK")
      })
  @Override
  public void update(ShardedBackupDto resource) {
    super.update(resource);
  }

  @Override
  protected void updateSpec(
      StackGresShardedBackup resourceToUpdate, StackGresShardedBackup resource) {
    resourceToUpdate.setSpec(resource.getSpec());
  }

}