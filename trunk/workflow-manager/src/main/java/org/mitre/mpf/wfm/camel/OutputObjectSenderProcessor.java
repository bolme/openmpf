/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2018 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2018 The MITRE Corporation                                       *
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

package org.mitre.mpf.wfm.camel;

import org.apache.camel.Exchange;
import org.mitre.mpf.interop.JsonJobRequest;
import org.mitre.mpf.interop.JsonOutputObjectSummary;
import org.mitre.mpf.wfm.WfmProcessingException;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateDao;
import org.mitre.mpf.wfm.data.access.hibernate.HibernateJobRequestDaoImpl;
import org.mitre.mpf.wfm.data.entities.persistent.JobRequest;
import org.mitre.mpf.wfm.enums.MpfHeaders;
import org.mitre.mpf.wfm.util.JsonUtils;
import org.mitre.mpf.wfm.util.PropertiesUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component(OutputObjectSenderProcessor.REF)
public class OutputObjectSenderProcessor extends WfmProcessor {
	private static final Logger log = LoggerFactory.getLogger(OutputObjectSenderProcessor.class);
	public static final String REF = "outputObjectSenderProcessor";

	@Autowired
	@Qualifier(HibernateJobRequestDaoImpl.REF)
	private HibernateDao<JobRequest> jobRequestDao;

	@Autowired
	private PropertiesUtil propertiesUtil;

	@Autowired
	private JsonUtils jsonUtils;

	@Override
	public void wfmProcess(Exchange exchange) throws WfmProcessingException {
		Long jobId = exchange.getIn().getHeader(MpfHeaders.JOB_ID, Long.class);
		assert jobId != null : String.format("The header '%s' (value=%s) was not set or is not a Long.", MpfHeaders.JOB_ID, exchange.getIn().getHeader(MpfHeaders.JOB_ID));

		try {
			JobRequest jobRequest = jobRequestDao.findById(jobId);
			JsonJobRequest jsonJobRequest = jsonUtils.deserialize(jobRequest.getInputObject(), JsonJobRequest.class);
			if (jsonJobRequest.isOutputObjectEnabled()) {
				JsonOutputObjectSummary jsonOutputObjectSummary = new JsonOutputObjectSummary(jobRequest.getOutputObjectPath());
				exchange.getOut().setHeader(MpfHeaders.SEND_OUTPUT_OBJECT, Boolean.TRUE);
				exchange.getOut().setHeader(MpfHeaders.JOB_ID, jobId);
				exchange.getOut().setBody(jsonUtils.serializeAsText(jsonOutputObjectSummary));
			}
		} catch(Exception exception) {
			log.error("Failed to create the output object summary for job {} due to an exception.", jobId, exception);
		}
	}
}