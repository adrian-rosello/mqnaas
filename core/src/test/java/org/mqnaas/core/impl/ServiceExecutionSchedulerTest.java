package org.mqnaas.core.impl;

/*
 * #%L
 * MQNaaS :: Core
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

import java.lang.reflect.InvocationTargetException;
import java.util.Date;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mqnaas.core.api.IExecutionService;
import org.mqnaas.core.api.IService;
import org.mqnaas.core.api.exceptions.ApplicationActivationException;
import org.mqnaas.core.api.exceptions.ServiceExecutionSchedulerException;
import org.mqnaas.core.api.scheduling.ServiceExecution;
import org.mqnaas.core.api.scheduling.Trigger;
import org.mqnaas.core.impl.samples.SampleService;
import org.mqnaas.core.impl.scheduling.ServiceExecutionScheduler;
import org.mqnaas.core.impl.scheduling.TriggerFactory;
import org.mqnaas.general.test.helpers.reflection.ReflectionTestHelper;
import org.powermock.api.mockito.PowerMockito;

/**
 * 
 * @author Adrián Roselló Rey (i2CAT)
 *
 */
public class ServiceExecutionSchedulerTest {

	IExecutionService			executionService;
	ServiceExecutionScheduler	scheduler;
	ServiceExecution			serviceExecution;
	IService					service;
	Trigger						trigger;
	Object[]					parameters;

	@Before
	public void prepareTest() throws SecurityException, IllegalArgumentException, IllegalAccessException, ApplicationActivationException {

		scheduler = new ServiceExecutionScheduler();

		executionService = PowerMockito.mock(IExecutionService.class);
		ReflectionTestHelper.injectPrivateField(scheduler, executionService, "executionService");

		scheduler.activate();

		service = new SampleService();

		// set trigger to current time + 0.5 seconds.
		trigger = TriggerFactory.create(new Date(System.currentTimeMillis() + 500L));

		parameters = new Object[2];
		parameters[0] = new String("param00");
		parameters[1] = new String("param01");

		serviceExecution = new ServiceExecution(service, trigger);
		serviceExecution.setService(service);
		serviceExecution.setTrigger(trigger);
		serviceExecution.setParameters(parameters);

	}

	@After
	public void shutdownTest() {
		scheduler.deactivate();
	}

	/**
	 * Test checks that the behaviour of a free-of-error scheduled use case. The trigger is set to current time + 0.5 seconds, so the test sleeps for
	 * 0.7 seconds in order to wait for the service to be executed.
	 * 
	 * @throws ServiceExecutionSchedulerException
	 * @throws InterruptedException
	 * @throws InvocationTargetException
	 */
	@Test
	public void correctSchedulingTest() throws ServiceExecutionSchedulerException, InterruptedException, InvocationTargetException {

		Assert.assertTrue(scheduler.getScheduledServiceExecutions().isEmpty());

		scheduler.schedule(serviceExecution);

		Assert.assertTrue(1 == scheduler.getScheduledServiceExecutions().size());

		Thread.sleep(1000);

		Assert.assertTrue(scheduler.getScheduledServiceExecutions().isEmpty());

		Mockito.verify(executionService, Mockito.times(1)).execute(service, parameters);

	}

	/**
	 * Test checks that the behaviour of a cancellation of an scheduled job.
	 * 
	 * @throws ServiceExecutionSchedulerException
	 * @throws InterruptedException
	 * @throws InvocationTargetException
	 */
	@Test
	public void cancelSchedulingTest() throws ServiceExecutionSchedulerException, InterruptedException, InvocationTargetException {

		Assert.assertTrue(scheduler.getScheduledServiceExecutions().isEmpty());

		scheduler.schedule(serviceExecution);

		Assert.assertTrue(1 == scheduler.getScheduledServiceExecutions().size());

		scheduler.cancel(serviceExecution);

		Assert.assertTrue(scheduler.getScheduledServiceExecutions().isEmpty());

		Mockito.verify(executionService, Mockito.times(0)).execute(service, parameters);

	}

	/**
	 * Test checks that the {@link ServiceExecutionScheduler} fails when the there's no provided {@link ServiceExecution}
	 * 
	 * @throws ServiceExecutionSchedulerException
	 */
	@Test(expected = IllegalArgumentException.class)
	public void nullServiceExecutionTest() throws ServiceExecutionSchedulerException {

		scheduler.schedule(null);

	}

}
