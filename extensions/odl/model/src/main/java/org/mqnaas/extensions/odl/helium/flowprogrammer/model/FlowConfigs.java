package org.mqnaas.extensions.odl.helium.flowprogrammer.model;

/*
 * #%L
 * MQNaaS :: ODL Model
 * %%
 * Copyright (C) 2007 - 2015 Fundació Privada i2CAT, Internet i
 * 			Innovació a Catalunya
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name = "list")
@XmlAccessorType(XmlAccessType.NONE)
public class FlowConfigs {
	@XmlElement
	List<FlowConfig> flowConfig;

	// To satisfy JAXB
	@SuppressWarnings("unused")
	private FlowConfigs() {
	}

	public FlowConfigs(List<FlowConfig> flowConfig) {
		this.flowConfig = flowConfig;
	}

	public List<FlowConfig> getFlowConfig() {
		return flowConfig;
	}

	public void setFlowConfig(List<FlowConfig> flowConfig) {
		this.flowConfig = flowConfig;
	}
}