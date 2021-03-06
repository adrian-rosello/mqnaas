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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.mqnaas.core.api.IApplication;
import org.mqnaas.core.api.ICapability;
import org.mqnaas.core.api.IExecutionService;
import org.mqnaas.core.api.IResource;
import org.mqnaas.core.api.IService;
import org.mqnaas.core.api.annotations.DependingOn;
import org.mqnaas.core.impl.dependencies.ApplicationInstanceLifeCycleState;
import org.mqnaas.core.impl.utils.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

//TODO Redo the security and instantiation aspects

/**
 * It manages an application instance's
 * <ul>
 * <li><b>class</b>: to represent it as long as no instance exists)</li>
 * <li><b>instance</b>: which could be given when initializing</li>
 * <li><b>two types of dependencies</b>
 * <ol>
 * <li><i>pending</i>: the unresolved application dependencies</li>
 * <li><i>resolved</i>: already resolved application dependencies</li>
 * </ol>
 * </ul>
 * 
 * Dependencies are identified using {@link DependingOn} field annotation.
 * 
 * <p>
 * Provides a proxy of the instance to be used when injecting this application (through {@link ApplicationProxyHolder})
 * </p>
 * <p>
 * Provides the {@link IService}s for each {@link IApplication} interface implemented by the represented application class (through
 * {@link ApplicationProxyHolder}).
 * </p>
 * 
 * @author Georg Mansky-Kummert (i2CAT)
 * @author Isart Canyameres Gimenez (i2cat)
 * @author Julio Carlos Barrera (i2CAT Foundation)
 */
public class ApplicationInstance {

	private final Logger						log	= LoggerFactory.getLogger(getClass());

	protected Class<? extends IApplication>		clazz;

	private IApplication						instance;

	private Dependencies						dependencies;

	// this field is used through reflection (take caution when refactoring)
	private IExecutionService					executionService;

	private ApplicationProxyHolder				proxyHolder;

	private ApplicationInstanceLifeCycleState	state;

	public ApplicationInstance(Class<? extends IApplication> clazz) {
		this(clazz, null);
	}

	public ApplicationInstance(Class<? extends IApplication> clazz, IApplication instance) {

		this.clazz = clazz;

		this.instance = instance;

		dependencies = new Dependencies(clazz, getInstance());

		proxyHolder = new ApplicationProxyHolder(clazz, getInstance());

		setState(ApplicationInstanceLifeCycleState.INSTANTIATED);
	}

	public Class<? extends IApplication> getClazz() {
		return clazz;
	}

	public IApplication getInstance() {
		if (instance == null) {
			try {
				instance = clazz.newInstance();
			} catch (InstantiationException e) {
				// ignore for now
				log.error("Failed to instantiate application " + clazz.getName(), e);
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// ignore for now
				log.error("Failed to instantiate application " + clazz.getName(), e);
				e.printStackTrace();
			}
		}

		return instance;
	}

	/**
	 * @return the injectedDependencies. Returned collection is not the live one, but a copy.
	 */
	public Collection<ApplicationInstance> getInjectedDependencies() {
		Collection<ApplicationInstance> injected = new ArrayList<ApplicationInstance>();
		for (Dependency resolved : dependencies.getResolvedDependencies()) {
			injected.add(resolved.getResolvedWith());
		}
		return injected;
	}

	/**
	 * @return the state
	 */
	public ApplicationInstanceLifeCycleState getState() {
		return state;
	}

	/**
	 * @param state
	 *            the state to set
	 */
	public void setState(ApplicationInstanceLifeCycleState state) {
		this.state = state;
	}

	/**
	 * Returns the currently pending dependencies, e.g. the missing capability classes.
	 */
	public Collection<Class<? extends IApplication>> getPendingClasses() {
		return dependencies.getPendingClasses();
	}

	/**
	 * Returns the currently resolved dependencies, e.g. the already initialized capability classes.
	 */
	public Collection<Class<? extends IApplication>> getResolvedClasses() {
		return dependencies.getResolvedClasses();
	}

	public boolean isResolved() {
		return dependencies.isResolved();
	}

	/**
	 * Resolves all dependencies that can be satisfied by the given {@link ApplicationInstance}.
	 * 
	 * @param potentialDependency
	 * @return whether the internal state of this instance has changed after this call or not (after the call is using potentialDependency and was not
	 *         before)
	 */
	public boolean resolve(ApplicationInstance dependency) {
		Collection<Dependency> affected = dependencies.resolveDependencies(dependency);

		Dependency execServiceDep = getExecutionServiceDependency(affected);
		if (execServiceDep != null) {
			// execution service has been resolved
			proxyHolder.setExecutionService(executionService);
		}

		return !affected.isEmpty();
	}

	/**
	 * Unresolves all dependencies that are being resolved with the given {@link CapabilityInstance}.
	 * 
	 * @param potentialDependency
	 * @return whether the internal state of this instance has changed after this call or not (was using given potential dependency and after the call
	 *         is no longer)
	 */
	public boolean unresolve(ApplicationInstance dependency) {
		Collection<Dependency> affected = dependencies.unresolveDependencies(dependency);

		Dependency execServiceDep = getExecutionServiceDependency(affected);
		if (execServiceDep != null) {
			// execution service has been unresolved
			proxyHolder.setExecutionService(null);
		}
		return !affected.isEmpty();
	}

	/**
	 * Unresolves all currently resolved dependencies
	 * 
	 */
	public void unresolveAllDependencies() {
		// Iterator-safe implementation for the following:
		// for (ApplicationInstance app: getInjectedDependencies()) unresolve(app);
		// Due to unresolve producing changes in the collections that backs up the foreach iterator, commented code is not safe
		// Copying injectedDependencies in another collection and iterate over that one solves the issue
		Collection<ApplicationInstance> injected = new HashSet<ApplicationInstance>(getInjectedDependencies());
		for (ApplicationInstance app : injected) {
			unresolve(app);
		}
	}

	// public void initServices() {
	// initInstanceServicesAndProxy(null);
	// }

	public void stopServices() {
		clearInstanceServicesAndProxy();
	}

	public Multimap<Class<? extends IApplication>, IService> getServices() {
		Multimap<Class<? extends IApplication>, IInternalService> internalServices = proxyHolder.getServices();
		Multimap<Class<? extends IApplication>, IService> services = ArrayListMultimap.create(internalServices.size(), 3);
		services.putAll(internalServices);
		return services;
	}

	/**
	 * Returns all application interfaces implemented by the represented application
	 * 
	 */
	public Collection<Class<? extends IApplication>> getApplications() {
		Set<Class<? extends IApplication>> appsCopy = new HashSet<Class<? extends IApplication>>();
		appsCopy.addAll(getServices().keySet());
		return appsCopy;
	}

	/**
	 * Sets the given {@link IResource} as the resource used when executing the services.
	 * 
	 * @param resource
	 *            The resources used when executing services.
	 */
	protected void setResource(IResource resource) {
		proxyHolder.setResource(resource);
	}

	protected void clearInstanceServicesAndProxy() {

		// 1. Clear proxy
		proxyHolder = null;

		// 2. Clear the instance
		instance = null;
	}

	public IApplication getProxy() {
		return proxyHolder.getProxy();
	}

	private Dependency getExecutionServiceDependency(Collection<Dependency> dependencies) {
		for (Dependency dep : dependencies) {
			if (dep.getInstance() == this && dep.getFieldType().equals(IExecutionService.class))
				return dep;
		}
		return null;
	}

	@Override
	public String toString() {

		StringBuilder sb = new StringBuilder();
		sb.append("Application ").append(clazz.getSimpleName());
		sb.append(" [pending=(");
		int i = 0;
		for (Class<? extends IApplication> clazz : getPendingClasses()) {
			if (i > 0)
				sb.append(", ");
			sb.append(clazz.getSimpleName());
			i++;
		}

		sb.append("), resolved=(");
		i = 0;
		for (Class<? extends IApplication> clazz : getResolvedClasses()) {
			if (i > 0)
				sb.append(", ");
			sb.append(clazz.getSimpleName());
			i++;
		}

		sb.append(")]");

		return sb.toString();
	}

	/**
	 * 
	 * @author Georg Mansky-Kummert (i2CAT)
	 * 
	 */
	private class Dependencies extends ArrayList<Dependency> {

		private static final long	serialVersionUID	= 1L;

		public Dependencies(Class<? extends IApplication> clazz, IApplication instance) {

			super();

			for (Field field : ReflectionUtils.getAnnotationFields(clazz, DependingOn.class)) {

				boolean isCore = field.getAnnotation(DependingOn.class).core();

				if (!(IApplication.class.isAssignableFrom(field.getType()))) {
					throw new IllegalArgumentException(
							"In " + clazz.getName() + " " + field.getType().getName() + " does not implement " + IApplication.class.getName() +
									" and can therefore not be used as a dependency.");
				}

				// The following cast is safe, because it was explicitly checked above
				@SuppressWarnings("unchecked")
				Class<? extends IApplication> type = (Class<? extends IApplication>) field.getType();

				add(new Dependency(field, type, instance, isCore));
			}

			if (!IExecutionService.class.isAssignableFrom(clazz)) {
				// add executionService dependency
				try {
					add(new Dependency(ApplicationInstance.class.getDeclaredField("executionService"), IExecutionService.class,
							ApplicationInstance.this, true));
				} catch (SecurityException e) {
					// This exception should never happen (a class should has access to its declared private fields)
					log.error("Error populating application dependencies. Unable to define execution service dependency: ", e);
				} catch (NoSuchFieldException e) {
					// This exception should never happen (unless a programmer removes/renames executionService field)
					log.error("Error populating application dependencies. Unable to define execution service dependency: ", e);
				}
			}

		}

		public Collection<Class<? extends IApplication>> getPendingClasses() {
			Collection<Class<? extends IApplication>> pendingClasses = new HashSet<Class<? extends IApplication>>();
			for (Dependency dep : getPendingDependencies()) {
				pendingClasses.add(dep.getFieldType());
			}
			return pendingClasses;
		}

		public Collection<Class<? extends IApplication>> getResolvedClasses() {
			Collection<Class<? extends IApplication>> resolvedClasses = new HashSet<Class<? extends IApplication>>();
			for (Dependency dep : getResolvedDependencies()) {
				resolvedClasses.add(dep.getFieldType());
			}
			return resolvedClasses;
		}

		private Iterable<Dependency> getResolvedDependencies() {
			Predicate<Dependency> isResolved = new Predicate<Dependency>() {
				@Override
				public boolean apply(Dependency dep) {
					return dep.isResolved();
				}
			};
			return Iterables.filter(this, isResolved);
		}

		private Iterable<Dependency> getPendingDependencies() {
			Predicate<Dependency> isPending = new Predicate<Dependency>() {
				@Override
				public boolean apply(Dependency dep) {
					return !dep.isResolved();
				}
			};
			return Iterables.filter(this, isPending);
		}

		public boolean isResolved() {
			for (Dependency dep : this) {
				if (!dep.isResolved())
					return false;
			}
			return true;
		}

		/**
		 * Unresolves all dependencies that are being resolved with the given {@link CapabilityInstance}.
		 * 
		 * @param potentialDependency
		 * @return whether the internal state of this instance has changed after this call or not (was using given potential dependency and after the
		 *         call is no longer)
		 */
		private Collection<Dependency> unresolveDependencies(ApplicationInstance potentialDependency) {
			Collection<Dependency> affected = new ArrayList<Dependency>();

			for (Dependency dep : getResolvedDependencies()) {
				if (dep.getResolvedWith() == potentialDependency) {
					// dependency is being resolved with potentialDependency
					dep.unresolve();
					affected.add(dep);
				}
			}
			return affected;
		}

		/**
		 * Resolves all dependencies that can be satisfied by the given {@link ApplicationInstance}.
		 * 
		 * @param potentialDependency
		 * @return whether the internal state of this instance has changed after this call or not (after the call is using potentialDependency and was
		 *         not before)
		 */
		private Collection<Dependency> resolveDependencies(ApplicationInstance potentialDependency) {

			Collection<Dependency> affected = new ArrayList<Dependency>();

			// if this ApplicationInstance is a CapabilityInstance
			if (ApplicationInstance.this instanceof CapabilityInstance) {
				IResource applicationInstanceResource = ((CapabilityInstance) ApplicationInstance.this).getResource();
				// iterate over pending dependencies
				for (Dependency dep : getPendingDependencies()) {

					// if dependency requires Core resource
					if (dep.coreDependency) {
						// if dep implements ICapability
						if (ICapability.class.isAssignableFrom(dep.getFieldType())) {
							// if potential dependency is a ICapability
							if (potentialDependency instanceof CapabilityInstance) {
								// if potential capability dependency has core as resource
								if (CoreProvider.isCore(((CapabilityInstance) potentialDependency).getResource())) {
									for (Class<? extends IApplication> capabilityClass : potentialDependency.getApplications()) {
										if (dep.isResolvedBy(capabilityClass)) {
											dep.resolveWith(potentialDependency);
											affected.add(dep);
										}
									}
								}
							}

						}
						// if dep is a pure IApplication
						else if (IApplication.class.isAssignableFrom(dep.getFieldType())) {
							log.error(dep.getFieldType() + " can not be annotated with " + DependingOn.class
									+ " and core parameter equals to true because IApplications have no associated IResource.");
						}
					}
					// dependency requires same resource
					else {
						// if dep implements ICapability
						if (ICapability.class.isAssignableFrom(dep.getFieldType())) {
							// if potential dependency is a ICapability
							if (potentialDependency instanceof CapabilityInstance) {
								// if potential capability dependency has core as resource
								if (((CapabilityInstance) potentialDependency).getResource().equals(applicationInstanceResource)) {
									for (Class<? extends IApplication> capabilityClass : potentialDependency.getApplications()) {
										if (dep.isResolvedBy(capabilityClass)) {
											dep.resolveWith(potentialDependency);
											affected.add(dep);
										}
									}
								}
							}
						}
						// if dep is a pure IApplication
						else if (IApplication.class.isAssignableFrom(dep.getFieldType())) {
							for (Class<? extends IApplication> capabilityClass : potentialDependency.getApplications()) {
								if (dep.isResolvedBy(capabilityClass)) {
									dep.resolveWith(potentialDependency);
									affected.add(dep);
								}
							}
						}
					}
				}
			}
			// if this ApplicationInstance is pure ApplicationInstance
			else {
				for (Dependency dep : getPendingDependencies()) {
					// if dependency requires Core resource
					if (dep.coreDependency) {
						// if dep implements ICapability
						if (ICapability.class.isAssignableFrom(dep.getFieldType())) {
							// if potential dependency is a ICapability
							if (potentialDependency instanceof CapabilityInstance) {
								// if potential capability dependency has core as resource
								if (CoreProvider.isCore(((CapabilityInstance) potentialDependency).getResource())) {
									for (Class<? extends IApplication> capabilityClass : potentialDependency.getApplications()) {
										if (dep.isResolvedBy(capabilityClass)) {
											dep.resolveWith(potentialDependency);
											affected.add(dep);
										}
									}
								}
							}
						}
						// if dep is a pure IApplication
						else if (IApplication.class.isAssignableFrom(dep.getFieldType())) {
							log.error(dep.getFieldType() + " can not be annotated with " + DependingOn.class
									+ " and core parameter equals to true because IApplications have no associated IResource.");
						}
					}
					// dependency requires same resource
					else {
						// if dep implements ICapability
						if (ICapability.class.isAssignableFrom(dep.getFieldType())) {
							// if potential dependency is a ICapability
							if (potentialDependency instanceof CapabilityInstance) {
								log.error("An IApplication can not have a " + DependingOn.class
										+ " annotated field beacuse pure IApplication's have no associated resource.");
							}
						}
						// if dep is a pure IApplication
						else if (IApplication.class.isAssignableFrom(dep.getFieldType())) {
							for (Class<? extends IApplication> capabilityClass : potentialDependency.getApplications()) {
								if (dep.isResolvedBy(capabilityClass)) {
									dep.resolveWith(potentialDependency);
									affected.add(dep);
								}
							}
						}
					}
				}
			}
			return affected;
		}
	}

	/**
	 * Represents a dependency in an instance that is to be resolved by an ApplicationInstance. Dependencies are identified by its declared type and
	 * the field that holds it in the instance.
	 * 
	 * @author Isart Canyameres Gimenez (i2cat)
	 * 
	 */
	private class Dependency {

		// the field identifying this Dependency
		private Field							field;
		// the type of this Dependency
		private Class<? extends IApplication>	fieldType;
		// the instance having this Dependency
		private Object							instance;

		// ApplicationInstance this Dependency is resolvedWith (the field is set with a proxy, not the ApplicationInstance itself)
		private ApplicationInstance				resolvedWith;

		// this dependency must be resolved with core resource
		private boolean							coreDependency	= false;

		public Dependency(Field field, Class<? extends IApplication> type, Object instance, boolean coreDependency) {
			this.field = field;
			this.fieldType = type;
			this.instance = instance;
			this.coreDependency = coreDependency;
		}

		/**
		 * Tells whether this Dependency can be resolved with given capabilityClass or not.
		 * 
		 * @param capabilityClass
		 * @return
		 */
		public boolean isResolvedBy(Class<? extends IApplication> capabilityClass) {
			// FIXME shouldn't it be is assignable from?
			return getFieldType().equals(capabilityClass);
		}

		public void resolveWith(ApplicationInstance potentialDependency) {
			log.debug("Resolving dependency of field {}.{} with {}", clazz.getSimpleName(), field.getName(), potentialDependency);
			resolve(potentialDependency.getProxy());
			setResolvedWith(potentialDependency);
		}

		public void unresolve() {
			log.debug("Unresolving dependency of field {}.{}", clazz.getSimpleName(), field.getName());
			resolve(null);
			setResolvedWith(null);
		}

		private void resolve(Object value) {
			// Initialize the field of the application or capability
			// TODO Security implications?
			try {
				field.setAccessible(true);
				field.set(instance, value);
			} catch (IllegalArgumentException e) {
				// ignore for now
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// ignore for now
				e.printStackTrace();
			}
		}

		/**
		 * @return the fieldType
		 */
		public Class<? extends IApplication> getFieldType() {
			return fieldType;
		}

		/**
		 * @return the instance
		 */
		public Object getInstance() {
			return instance;
		}

		/**
		 * @return the resolvedWith
		 */
		public ApplicationInstance getResolvedWith() {
			return resolvedWith;
		}

		/**
		 * @param resolvedWith
		 *            the resolvedWith to set
		 */
		public void setResolvedWith(ApplicationInstance resolvedWith) {
			this.resolvedWith = resolvedWith;
		}

		public boolean isResolved() {
			return getResolvedWith() != null;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("Dependency [");
			sb.append("type=").append(fieldType);
			sb.append(", name=").append(field.getName());
			sb.append(", core=").append(coreDependency);
			sb.append(", instance=").append(instance.getClass().getName());
			sb.append("]");
			return sb.toString();
		}

	}
}
