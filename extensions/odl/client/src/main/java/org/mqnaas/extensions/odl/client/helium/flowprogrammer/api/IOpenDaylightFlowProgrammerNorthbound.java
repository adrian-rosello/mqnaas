package org.mqnaas.extensions.odl.client.helium.flowprogrammer.api;

/*
 * #%L
 * OpenNaaS :: OpenFlow Switch :: OpenDaylight
 * %%
 * Copyright (C) 2007 - 2014 Fundació Privada i2CAT, Internet i Innovació a Catalunya
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

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.mqnaas.extensions.odl.helium.flowprogrammer.model.FlowConfig;
import org.mqnaas.extensions.odl.helium.flowprogrammer.model.FlowConfigs;




@Path("/controller/nb/v2/flowprogrammer/default")
public interface IOpenDaylightFlowProgrammerNorthbound {

    /**
     * Adds a static flow.
     *
     * @param flow The flow to push.
     * @param DPID
     * @param name
     * @throws java.lang.Exception
     */
    @Path("/node/OF/{DPID}/staticFlow/{name}")
    @PUT
    @Consumes(MediaType.APPLICATION_XML)//content-type
    @Produces(MediaType.APPLICATION_XML)
    public void addOrModifyFlow(FlowConfig flow, @PathParam("DPID") String DPID, @PathParam("name") String name) throws Exception;

    /**
     * Deletes a static flow
     *
     * @param DPID
     * @param name
     * @throws java.lang.Exception
     */
    @Path("/node/OF/{DPID}/staticFlow/{name}")
    @DELETE
    @Consumes(MediaType.APPLICATION_XML)
    public void deleteFlow(@PathParam("DPID") String DPID, @PathParam("name") String name) throws Exception;
    
    /**
     * Get static flow.
     *
     * @param DPID
     * @param name
     * @return 
     * @throws java.lang.Exception
     */
    @Path("/node/OF/{DPID}/staticFlow/{name}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public FlowConfig getStaticFlow(@PathParam("DPID") String DPID, @PathParam("name") String name) throws Exception;
    
    /**
     * Gets a list of flows by switch
     *
     * @param dpid
     * @return
     * @throws java.lang.Exception
     */
    @Path("/node/OF/{DPID}")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public FlowConfigs getStaticFlows(@PathParam("DPID") String dpid) throws Exception;
    
    /**
     * Gets all list of all flows
     * @return 
     * @throws java.lang.Exception
     */
    @Path("/")
    @GET
    @Produces(MediaType.APPLICATION_XML)
    public FlowConfigs getStaticFlows() throws Exception;

}
