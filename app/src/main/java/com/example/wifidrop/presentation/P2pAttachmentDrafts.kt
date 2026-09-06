package com.example.wifidrop.presentation

enum class P2pAttachmentContext { FILES, DIRECT_CHAT, CHANNEL }

data class P2pAttachmentDraft<T>(
    val files: List<T> = emptyList(),
    val status: String = ""
)

/** Each composer owns its selection; changing screens never moves files between audiences. */
data class P2pAttachmentDrafts<T>(
    val activeContext: P2pAttachmentContext = P2pAttachmentContext.FILES,
    val drafts: Map<P2pAttachmentContext, P2pAttachmentDraft<T>> = emptyMap()
) {
    fun draft(context: P2pAttachmentContext = activeContext): P2pAttachmentDraft<T> =
        drafts[context] ?: P2pAttachmentDraft()

    fun select(context: P2pAttachmentContext): P2pAttachmentDrafts<T> = copy(activeContext = context)

    fun update(
        context: P2pAttachmentContext,
        transform: (P2pAttachmentDraft<T>) -> P2pAttachmentDraft<T>
    ): P2pAttachmentDrafts<T> = copy(drafts = drafts + (context to transform(draft(context))))
}
