/**
 * Write-side ports for the problem domain.
 *
 * <p>{@link com.ulticode.modules.problem.port.ProblemDetailDomainPort} owns the
 * detail-satellite write lifecycle (problem_details row + languages + examples
 * + tag relations). The read side lives in
 * {@code com.ulticode.modules.problem.projection}.
 */
package com.ulticode.modules.problem.port;
