package io.repaint.maven.rangeresolver

import groovy.transform.CompileStatic
import org.apache.maven.AbstractMavenLifecycleParticipant
import org.apache.maven.MavenExecutionException
import org.apache.maven.RepositoryUtils
import org.apache.maven.artifact.repository.ArtifactRepository
import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Dependency
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.component.annotations.Component
import org.codehaus.plexus.component.annotations.Requirement
import org.eclipse.aether.RepositorySystemSession
import org.eclipse.aether.artifact.ArtifactTypeRegistry
import org.eclipse.aether.impl.VersionRangeResolver
import org.eclipse.aether.resolution.VersionRangeRequest
import org.eclipse.aether.resolution.VersionRangeResult

/**
 ** by Richard Vowles (https://www.google.com/+RichardVowles)
 ** (c) 2014 - Blue Train Software Ltd
 */
@CompileStatic
@Component(role = AbstractMavenLifecycleParticipant, hint = "RangeResolverMavenLifecycleParticipant")
class RangeResolverMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {
	@Requirement
	VersionRangeResolver versionRangeResolver

	@Requirement
	ArtifactTypeRegistry artifactTypeRegistry

	RepositorySystemSession repositorySystemSession
	List<ArtifactRepository> remoteRepositories
	ArtifactRepository localRepository

	/**
	 * Invoked after all MavenProject instances have been created.
	 *
	 * This callback is intended to allow extensions to manipulate MavenProjects
	 * before they are sorted and actual build execution starts.
	 */
	public void afterProjectsRead(MavenSession mavenSession)
		throws MavenExecutionException {

		this.remoteRepositories = mavenSession.request.remoteRepositories
		this.localRepository = mavenSession.request.localRepository
		this.repositorySystemSession = mavenSession.repositorySession

		final MavenProject topLevelProject = mavenSession.getTopLevelProject()
		List<String> subModules = topLevelProject.getModules()

		if (subModules != null && subModules.size() > 0) {
			//We're in a multi-module build, we need to trigger model merging on all sub-modules
			for (MavenProject subModule : mavenSession.getProjects()) {
				if (subModule != topLevelProject) {
					resolveRanges(subModule)
				}
			}
		} else {
			resolveRanges(topLevelProject)
		}
	}

	/**
	 * This won't work for a number of reasons.
	 *
	 * (a) This triggers before the release plugin runs, so we don't know we are releasing
	 * (b) we only have dependencies. We really need to replace the DefaultArtifactResolver with one of our own.
	 *
	 * @param project
	 */


	protected void resolveRanges(MavenProject project) {
		boolean alwaysHigh = System.getProperty("performRelease") == "true"

		project?.dependencies?.each { Dependency dependency ->
			resolveRange(dependency, alwaysHigh)
		}
	}

	void resolveRange(Dependency dependency, boolean alwaysHigh) {
		VersionRangeRequest versionRangeRequest = new VersionRangeRequest(
			RepositoryUtils.toDependency(dependency, artifactTypeRegistry).artifact,
			RepositoryUtils.toRepos(remoteRepositories), null)

		VersionRangeResult versionRangeResult = versionRangeResolver.resolveVersionRange(repositorySystemSession, versionRangeRequest)

		if (versionRangeResult.versions) {
			if (alwaysHigh || dependency.scope == "test") {
				dependency.version = versionRangeResult.highestVersion
			} else {
				dependency.version = versionRangeResult.lowestVersion
			}
		}
	}
}
