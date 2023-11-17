/*
 * Copyright (C) 2019 OnGres, Inc.
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.stackgres.operator.validation;

import io.quarkus.runtime.StartupEvent;
import io.stackgres.common.CdiUtil;
import io.stackgres.operator.common.StackGresDistributedLogsReview;
import io.stackgres.operatorframework.admissionwebhook.AdmissionReviewResponse;
import io.stackgres.operatorframework.admissionwebhook.validating.ValidationPipeline;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path(ValidationUtil.DISTRIBUTED_LOGS_VALIDATION_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DistributedLogsValidationResource
    extends AbstractValidationResource<StackGresDistributedLogsReview> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(DistributedLogsValidationResource.class);

  @Inject
  public DistributedLogsValidationResource(
      ValidationPipeline<StackGresDistributedLogsReview> pipeline) {
    super(pipeline);
  }

  public DistributedLogsValidationResource() {
    super(null);
    CdiUtil.checkPublicNoArgsConstructorIsCalledToCreateProxy(getClass());
  }

  void onStart(@Observes StartupEvent ev) {
    LOGGER.info("SgProfile validation resource started");
  }

  @Override
  @POST
  public AdmissionReviewResponse validate(StackGresDistributedLogsReview admissionReview) {
    return super.validate(admissionReview);
  }
}
