package io.meshcloud.dockerosb.persistence

import io.meshcloud.dockerosb.metrics.MetricType
import io.meshcloud.dockerosb.metrics.ServiceInstanceDatapoints
import io.meshcloud.dockerosb.metrics.gauge.GaugeMetricModel
import io.meshcloud.dockerosb.metrics.periodiccounter.PeriodicCounterMetricModel
import io.meshcloud.dockerosb.metrics.samplingcounter.SamplingCounterMetricModel
import io.meshcloud.dockerosb.model.ServiceInstance
import io.meshcloud.dockerosb.model.Status
import org.springframework.cloud.servicebroker.model.instance.OperationState
import org.springframework.stereotype.Component
import java.io.File
import java.time.Instant

@Component
class ServiceInstanceRepository(private val yamlHandler: YamlHandler, private val gitHandler: GitHandler) {

  fun createServiceInstance(serviceInstance: ServiceInstance) {
    val serviceInstanceId = serviceInstance.serviceInstanceId

    val instanceYml = serviceInstanceYmlFile(serviceInstanceId)

    yamlHandler.writeObject(
        objectToWrite = serviceInstance,
        file = instanceYml
    )
    gitHandler.commitAllChanges(
        commitMessage = "Created Service instance $serviceInstanceId"
    )
  }

  // TODO Check if an update request is allowed. See https://github.com/meshcloud/unipipe-service-broker/pull/35/files#r651527916
  // Right now we don't apply any validation and trust the marketplace to know what it's doing.
  //
  //  There are several actions that can be [triggered via an update request](https://github.com/openservicebrokerapi/servicebroker/blob/master/spec.md#updating-a-service-instance):
  //- updating the plan a service instance is using, if `plan_updateable` is true
  //- updating the context object of a service instance, if `allow_context_updates` is true
  //- applying a maintenance update, if the service broker previously provided `maintenance_info` to the platform.
  fun updateServiceInstance(serviceInstance: ServiceInstance): Status {
    val serviceInstanceId = serviceInstance.serviceInstanceId

    val instanceYml = serviceInstanceYmlFile(serviceInstanceId)
    yamlHandler.writeObject(
      objectToWrite = serviceInstance,
      file = instanceYml
    )

    val statusYml = serviceInstanceStatusYmlFile(serviceInstanceId)
    val status = Status("in progress", "updating service")
    yamlHandler.writeObject(
        objectToWrite = status,
        file = statusYml
    )

    gitHandler.commitAllChanges(
      commitMessage = "Updated Service instance $serviceInstanceId"
    )

    return status
  }

  fun deleteServiceInstance(serviceInstance: ServiceInstance) {
    val serviceInstanceId = serviceInstance.serviceInstanceId

    val instanceYml = serviceInstanceYmlFile(serviceInstanceId)
    serviceInstance.deleted = true
    yamlHandler.writeObject(
        objectToWrite = serviceInstance,
        file = instanceYml
    )

    val statusYml = serviceInstanceStatusYmlFile(serviceInstanceId)
    val status = Status("in progress", "preparing service deletion")
    yamlHandler.writeObject(
        objectToWrite = status,
        file = statusYml
    )

    gitHandler.commitAllChanges(commitMessage = "Marked Service instance $serviceInstanceId as deleted.")
  }

  fun tryGetServiceInstance(serviceInstanceId: String): ServiceInstance? {
    val instanceYml = serviceInstanceYmlFile(serviceInstanceId)

    if (!instanceYml.exists()) {
      return null
    }

    return yamlHandler.readObject(instanceYml, ServiceInstance::class.java)
  }

  fun tryGetServiceInstanceGaugeMetrics(serviceInstanceId: String, from: Instant, to: Instant): List<ServiceInstanceDatapoints<GaugeMetricModel>> {
    val instanceMetricsYmlFiles = serviceInstanceMetricsYmlFiles(serviceInstanceId, MetricType.GAUGE)
    val serviceInstanceDatapointsList: MutableList<ServiceInstanceDatapoints<GaugeMetricModel>> = mutableListOf()
    val combinedInstanceMetricsList =  instanceMetricsYmlFiles.map{yamlHandler.readGeneric<ServiceInstanceDatapoints<GaugeMetricModel>>(it)}
    val serviceInstanceIdGroup = combinedInstanceMetricsList.groupBy { it.serviceInstanceId }

    for (uniqueServiceInstance in serviceInstanceIdGroup){
      val resourceGroup = uniqueServiceInstance.value.groupBy { it.resource }
      for (uniqueResource in resourceGroup ){
        val filteredValues = uniqueResource.value
          .flatMap { serviceInstanceDatapoints: ServiceInstanceDatapoints<GaugeMetricModel> -> serviceInstanceDatapoints.values }
          .filterNot { gaugeMetricModel: GaugeMetricModel -> gaugeMetricModel.observedAt < from || gaugeMetricModel.observedAt > to }
          .sortedBy { gaugeMetricModel: GaugeMetricModel -> gaugeMetricModel.observedAt   }
        if (filteredValues.isNotEmpty())
          serviceInstanceDatapointsList += ServiceInstanceDatapoints(uniqueServiceInstance.key,uniqueResource.key,filteredValues)
      }
    }

    return serviceInstanceDatapointsList
  }

  fun tryGetServiceInstancePeriodicCounterMetrics(serviceInstanceId: String, from: Instant, to: Instant): List<ServiceInstanceDatapoints<PeriodicCounterMetricModel>> {
    val instanceMetricsYmlFiles = serviceInstanceMetricsYmlFiles(serviceInstanceId, MetricType.PERIODIC)
    // PERIODIC METRIC TYPE HAS DIFFERENT PARAMETER TO DECIDE TIME-FILTERING
    val serviceInstanceDatapointsList: MutableList<ServiceInstanceDatapoints<PeriodicCounterMetricModel>> = mutableListOf()
    val combinedInstanceMetricsList =  instanceMetricsYmlFiles.map{yamlHandler.readGeneric<ServiceInstanceDatapoints<PeriodicCounterMetricModel>>(it)}
    val serviceInstanceIdGroup = combinedInstanceMetricsList.groupBy { it.serviceInstanceId }

    for (uniqueServiceInstance in serviceInstanceIdGroup){
      val resourceGroup = uniqueServiceInstance.value.groupBy { it.resource }
      for (uniqueResource in resourceGroup ){
        val filteredValues = uniqueResource.value
          .flatMap { serviceInstanceDatapoints: ServiceInstanceDatapoints<PeriodicCounterMetricModel> -> serviceInstanceDatapoints.values }
          .filterNot { periodicCounterMetricModel: PeriodicCounterMetricModel -> periodicCounterMetricModel.periodStart < from || periodicCounterMetricModel.periodEnd > to }
          .sortedBy { periodicCounterMetricModel: PeriodicCounterMetricModel -> periodicCounterMetricModel.periodStart  }
        if (filteredValues.isNotEmpty())
          serviceInstanceDatapointsList += ServiceInstanceDatapoints(uniqueServiceInstance.key,uniqueResource.key,filteredValues)
      }
    }

    return serviceInstanceDatapointsList
  }

  fun tryGetServiceInstanceSamplingCounterMetrics(serviceInstanceId: String, from: Instant, to: Instant): List<ServiceInstanceDatapoints<SamplingCounterMetricModel>> {
    val instanceMetricsYmlFiles = serviceInstanceMetricsYmlFiles(serviceInstanceId, MetricType.SAMPLING)
    val serviceInstanceDatapointsList: MutableList<ServiceInstanceDatapoints<SamplingCounterMetricModel>> = mutableListOf()
    val combinedInstanceMetricsList =  instanceMetricsYmlFiles.map{yamlHandler.readGeneric<ServiceInstanceDatapoints<SamplingCounterMetricModel>>(it)}
    val serviceInstanceIdGroup = combinedInstanceMetricsList.groupBy { it.serviceInstanceId }

    for (uniqueServiceInstance in serviceInstanceIdGroup){
      val resourceGroup = uniqueServiceInstance.value.groupBy { it.resource }
      for (uniqueResource in resourceGroup ){
        val filteredValues = uniqueResource.value
          .flatMap { serviceInstanceDatapoints: ServiceInstanceDatapoints<SamplingCounterMetricModel> -> serviceInstanceDatapoints.values }
          .filterNot { samplingCounterMetricModel: SamplingCounterMetricModel -> samplingCounterMetricModel.observedAt < from || samplingCounterMetricModel.observedAt > to }
          .sortedBy { samplingCounterMetricModel: SamplingCounterMetricModel -> samplingCounterMetricModel.observedAt  }
        if (filteredValues.isNotEmpty())
          serviceInstanceDatapointsList += ServiceInstanceDatapoints(uniqueServiceInstance.key,uniqueResource.key,filteredValues)
      }
    }

    return serviceInstanceDatapointsList
  }


  fun findInstancesByServiceId(serviceDefinitionId: String): List<ServiceInstance> {
    val files = gitHandler.instancesDirectory().listFiles()
      ?: return emptyList()

    return files
        .map { gitHandler.fileInRepo(gitHandler.instanceYmlRelativePath(it.name)) }
        .filter { it.exists() }
        .sortedBy { it.lastModified() }
        .map { yamlHandler.readObject(it, ServiceInstance::class.java) }
        .filter { it.serviceDefinitionId == serviceDefinitionId }
  }

  fun getServiceInstanceStatus(serviceInstanceId: String): Status {
    val statusYml = serviceInstanceStatusYmlFile(serviceInstanceId)

    return when (statusYml.exists()) {
      true -> yamlHandler.readObject(statusYml, Status::class.java)
      else -> Status(
          status = OperationState.IN_PROGRESS.value,
          description = "preparing deployment"
      )
    }
  }

  private fun serviceInstanceMetricsYmlFiles(serviceInstanceId: String, metricType: MetricType): List<File> {
    return gitHandler.filesInRepo(instanceFolderPath(serviceInstanceId)).filter { it.name.startsWith(metricType.name.first().lowercase() + "-metrics") &&
        it.name.endsWith(".yml") }.toList()
  }

  private fun serviceInstanceYmlFile(serviceInstanceId: String): File {
    val instanceYmlPath = instanceFolderPath(serviceInstanceId) + "/instance.yml"

    return gitHandler.fileInRepo(instanceYmlPath)
  }

  private fun serviceInstanceStatusYmlFile(serviceInstanceId: String): File {
    val instanceYmlPath = instanceFolderPath(serviceInstanceId) + "/status.yml"

    return gitHandler.fileInRepo(instanceYmlPath)
  }

  private fun instanceFolderPath(serviceInstanceId: String): String {
    return "instances/$serviceInstanceId"
  }
}

