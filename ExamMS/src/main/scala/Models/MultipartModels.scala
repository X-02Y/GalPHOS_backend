package Models

// Multipart form data models for file uploads
case class MultipartFileUploadRequest(
  originalName: String,
  fileType: String, // "question", "answer", "answerSheet"
  fileBytes: Array[Byte]
)
