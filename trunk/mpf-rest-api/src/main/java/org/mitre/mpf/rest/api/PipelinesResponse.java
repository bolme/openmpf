/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.rest.api;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

// swagger includes
@Api(value = "pipelines")
@ApiModel(description="PipelinesResponse model")
public class PipelinesResponse {

    private String pipelineName;
    @ApiModelProperty(position=1, required = true)
    public String getPipelineName() { return pipelineName; }
    public void setPipelineName(String pipelineName) { this.pipelineName = pipelineName; }

    private boolean supportsBatch;
    @ApiModelProperty(position=2, required = true)
    public boolean getSupportsBatch() { return supportsBatch; }
    public void setSupportsBatch(boolean supportsBatch) { this.supportsBatch = supportsBatch; }

    private boolean supportsStreaming;
    @ApiModelProperty(position=3, required = true)
    public boolean getSupportsStreaming() { return supportsStreaming; }
    public void setSupportsStreaming(boolean supportsStreaming) { this.supportsStreaming = supportsStreaming; }

    public PipelinesResponse() {}

    public PipelinesResponse( String pipelineName, boolean supportsBatch, boolean supportsStreaming ) {
        this.pipelineName = pipelineName;
        this.supportsBatch = supportsBatch;
        this.supportsStreaming = supportsStreaming;
    }
}