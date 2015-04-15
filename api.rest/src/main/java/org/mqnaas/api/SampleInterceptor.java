package org.mqnaas.api;

/*
 * #%L
 * MQNaaS :: REST API Provider
 * %%
 * Copyright (C) 2007 - 2015 Fundació Privada i2CAT, Internet i Innovació a Catalunya
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-3.0.html>.
 * #L%
 */

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Link;

import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.mqnaas.core.api.ICapability;
import org.mqnaas.core.api.IResource;
import org.mqnaas.core.api.annotations.ListsResources;

/**
 * <p>
 * Sample interceptor adding to response header the {@link Link}s of the {@link IResource}s.
 * </p>
 * <p>
 * This first spike of the interceptor reacts when the {@link ListsResources} method of a management {@link ICapability} is invoked through the REST
 * API. It invokes once again this service in order to get all managed {@link IResource}s, and create a {@link Link} that is added to the response
 * headers.
 * </p>
 * 
 * @author Adrián Roselló Rey (i2CAT)
 *
 */
public class SampleInterceptor extends AbstractPhaseInterceptor<Message> {

	Class<? extends ICapability>	capability;
	ICapability						capabilityInstance;

	public SampleInterceptor() {
		super(Phase.WRITE);
	}

	public SampleInterceptor(Class<? extends ICapability> capability, ICapability capabilityInstance) {
		super(Phase.WRITE);
		this.capability = capability;
		this.capabilityInstance = capabilityInstance;
	}

	@Override
	public void handleMessage(Message message) throws Fault {

		Map<String, List> headers = getHeaders(message);

		String targetUrl = getTargetUrl(message);
		Method targetMethod = getTargetMethod(message);

		try {
			Method service = getMappedService(targetMethod);

			if (service != null) {

				if (isListResorcesService(service)) {

					List<IResource> resources = (List<IResource>) service.invoke(capabilityInstance);
					for (IResource resource : resources) {
						Link.Builder linkBuilder = new Link.Builder();
						linkBuilder.uri(targetUrl + "/" + resource.getId());
						linkBuilder.rel("/" + resource.getId());
						Link link = linkBuilder.build();
						headers.put("Link", Collections.singletonList(link));
					}
				}

				message.put(Message.PROTOCOL_HEADERS, headers);

			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private Method getMappedService(Method targetMethod) throws NoSuchMethodException, SecurityException {
		Method service = null;
		for (Method candidateMethod : capability.getMethods()) {
			if (candidateMethod.getName().equals(targetMethod.getName()) && (candidateMethod.getParameterTypes().length == 0))
				service = capability.getDeclaredMethod(targetMethod.getName());
		}

		return service;
	}

	/**
	 * Returns the response headers.
	 */
	private Map<String, List> getHeaders(Message message) {
		Map<String, List> headers = (Map<String, List>) message.get(Message.PROTOCOL_HEADERS);

		if (headers == null)
			headers = new HashMap<String, List>();

		return headers;
	}

	/**
	 * Returns the JAVA {@link Method} mapped to the request's URL.
	 */
	private Method getTargetMethod(Message message) {
		OperationResourceInfo resourceInfo = message.getExchange().get(OperationResourceInfo.class);
		return resourceInfo.getMethodToInvoke();
	}

	/**
	 * Returns the URL of the request.
	 */
	private String getTargetUrl(Message message) {
		return message.getExchange().getEndpoint().getEndpointInfo().getAddress();
	}

	/**
	 * Checks if the given method is annotated with the {@link ListsResources} method.
	 */
	private boolean isListResorcesService(Method service) {
		return service.getAnnotation(ListsResources.class) != null;
	}
}
