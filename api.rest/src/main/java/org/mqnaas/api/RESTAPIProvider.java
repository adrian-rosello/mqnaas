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

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.mqnaas.api.exceptions.InvalidCapabilityDefinionException;
import org.mqnaas.api.mapping.APIMapper;
import org.mqnaas.api.writers.InterfaceWriter;
import org.mqnaas.core.api.ICapability;
import org.mqnaas.core.api.IRootResource;
import org.mqnaas.core.api.Specification;
import org.mqnaas.core.api.exceptions.ApplicationActivationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core implementation of the REST API provision.
 * 
 * Supports the publication of specific capability interfaces implemented by a capability implementation at a given URI.
 * 
 * @author Georg Mansky-Kummert (i2CAT)
 * 
 */
public class RESTAPIProvider implements IRESTAPIProvider {

	private static final Logger												log				= LoggerFactory.getLogger(RESTAPIProvider.class);

	private RESTAPIGenerator												apiGenerator	= new RESTAPIGenerator();

	private Map<Pair<ICapability, Class<? extends ICapability>>, Server>	servers			= new HashMap<Pair<ICapability, Class<? extends ICapability>>, Server>();

	public void publish(ICapability capability, Class<? extends ICapability> interfaceToBePublished, String uri)
			throws InvalidCapabilityDefinionException {

		// 2. Add the classloader of the interface to be published to the proxy classloader of the bean
		ServerFactory factoryBean = new ServerFactory(interfaceToBePublished, uri);

		// 3. Create the API interface reflecting the interface to be published at the given uri
		InterfaceWriter interfaceWriter = new InterfaceWriter(interfaceToBePublished, uri);

		Class<?> apiInterface = interfaceWriter.toClass(factoryBean.getClassLoader());

		// 4. Create a mapper mapping the both interfaces
		APIMapper mapper = apiGenerator.createAPIInterface(apiInterface, interfaceWriter, interfaceToBePublished, capability);

		// 5. Publish the API interface using the mapper as the invocation handler of the proxy
		factoryBean.setResourceClasses(apiInterface);

		Object proxy = Proxy.newProxyInstance(factoryBean.getClassLoader(), new Class<?>[] { apiInterface }, mapper);

		factoryBean.setResourceProvider(apiInterface, new SingletonResourceProvider(proxy));

		Server server = factoryBean.create();

		// 6. Add "HATEAOS" interceptor
		server.getEndpoint().getOutInterceptors().add(new SampleInterceptor(interfaceToBePublished, capability));

		servers.put(new ImmutablePair<ICapability, Class<? extends ICapability>>(capability, interfaceToBePublished), server);

		log.debug("Published {} at {}", interfaceToBePublished, factoryBean.getAddress());
		log.trace(interfaceWriter.toString());
	}

	@Override
	public boolean unpublish(ICapability capability, Class<? extends ICapability> interfaceToUnPublish) {
		Pair<ICapability, Class<? extends ICapability>> key = new ImmutablePair<ICapability, Class<? extends ICapability>>(capability,
				interfaceToUnPublish);
		Server server = servers.get(key);

		boolean unpublished = false;

		if (server != null) {
			String addr = server.getEndpoint().getEndpointInfo().getAddress();
			server.destroy();
			servers.remove(key);

			unpublished = true;

			// System.out.println("Unpublished from " + addr);
			log.debug("Unpublished {} at {}", interfaceToUnPublish, addr);
		}

		return unpublished;
	}

	public static boolean isSupporting(IRootResource resource) {
		return resource.getDescriptor().getSpecification().getType() == Specification.Type.CORE;
	}

	@Override
	public void activate() throws ApplicationActivationException {
	}

	@Override
	public void deactivate() {
	}

}
