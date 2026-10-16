package com.snehith.documentvault

/** Backup planning is pure so corrupted raw images never invalidate a whole vault export. */
data class BackupPlan(val cards:List<VaultCard>,val availableIds:Set<String>,
    val warnings:List<String>)
object BackupIntegrity {
    fun clearMissingOriginals(cards:List<VaultCard>,exists:(String)->Boolean):List<VaultCard> =
        cards.map { card-> card.copy(files=card.files.map { file->
            if(file.originalId.isBlank()||exists(file.originalId)) file
            else file.copy(originalId="",cropCorners="",cropRotation=0)
        }) }
    fun plan(cards:List<VaultCard>,exists:(String)->Boolean):BackupPlan {
        val missing=mutableListOf<String>()
        val kept=clearMissingOriginals(cards,exists)
        cards.flatMap{it.files}.forEach{file->
            if(!exists(file.id)) missing.add("Required attachment missing: "+file.name)
            if(file.originalId.isNotBlank()&&!exists(file.originalId))
                missing.add("Optional original unavailable: "+file.name)
        }
        return BackupPlan(kept,AttachmentReferences.referencedIds(kept).filter(exists).toSet(),missing)
    }
}
