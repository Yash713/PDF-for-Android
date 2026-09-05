package com.pdfmaster.app.data.remote.dto

/**
 * DTOs for CloudConvert's v2 job API (https://cloudconvert.com/api/v2).
 * A conversion is one job with three chained tasks: upload the source file,
 * convert it, then export a download URL for the result.
 */

data class CreateJobRequest(
    val tasks: Map<String, Map<String, String>>
)

data class JobEnvelope(
    val data: JobResponse
)

data class JobResponse(
    val id: String,
    val status: String,
    val tasks: List<TaskResponse>
)

data class TaskResponse(
    val id: String,
    val name: String,
    val operation: String,
    val status: String,
    val result: TaskResult?
)

data class TaskResult(
    val form: UploadForm?,
    val files: List<ResultFile>?
)

data class UploadForm(
    val url: String,
    val parameters: Map<String, String>
)

data class ResultFile(
    val url: String,
    val filename: String
)
