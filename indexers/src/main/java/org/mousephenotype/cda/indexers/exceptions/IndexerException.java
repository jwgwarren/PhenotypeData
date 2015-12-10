/*******************************************************************************
 * Copyright 2015 EMBL - European Bioinformatics Institute
 *
 * Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific
 * language governing permissions and limitations under the
 * License.
 *******************************************************************************/
package org.mousephenotype.cda.indexers.exceptions;

import org.mousephenotype.cda.enumerations.RunStatus;

/**
 * @author Matt Pearce
 */
public class IndexerException extends Exception {

	private static final long serialVersionUID = 1L;
	private RunStatus runStatus = RunStatus.FAIL;

	public IndexerException() {
		super();
	}

	public IndexerException(String message) {
		super(message);
	}

	public IndexerException(Throwable cause) {
		super(cause);
	}

	public IndexerException(String message, Throwable cause) {
		super(message, cause);
	}

	public IndexerException(String message, RunStatus runStatus) {
		super(message);
		this.runStatus = runStatus;
	}

	public RunStatus getRunStatus() { return runStatus; }
}
