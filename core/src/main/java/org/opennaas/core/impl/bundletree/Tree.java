/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opennaas.core.impl.bundletree;

/**
 * Represents a tree that can be written to the console.
 * 
 * The output will look like this:
 * 
 * <pre>
 * root
 * 		+ -child1
 * 		| +-grandchild
 * 		+ -child2
 * </pre>
 */
public class Tree<T> extends Node<T> {

	/**
	 * Creates a new tree with the given root node
	 * 
	 * @param root
	 *            the root node
	 */
	public Tree(T root) {
		super(root);
	}

}