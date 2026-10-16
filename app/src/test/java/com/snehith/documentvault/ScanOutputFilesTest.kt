package com.snehith.documentvault

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class ScanOutputFilesTest {
    @get:Rule val temp=TemporaryFolder()

    @Test fun cropSaveCreatesCameraDirectoryEvenWhenMissing(){
        val cache=temp.newFolder("cache")
        val camera=File(cache,"camera")
        assertFalse(camera.exists())
        val output=ScanOutputFiles.newScan(cache)
        assertTrue(camera.isDirectory)
        assertTrue(output.isFile)
        assertEquals(camera.canonicalFile,output.parentFile.canonicalFile)
        assertTrue(output.name.startsWith("scan-"))
        assertTrue(output.name.endsWith(".jpg"))
    }

    @Test fun cropSaveRecreatesCameraDirectoryAfterCacheCleanup(){
        val cache=temp.newFolder("cache")
        val first=ScanOutputFiles.newScan(cache)
        assertTrue(first.parentFile.deleteRecursively())
        val second=ScanOutputFiles.newScan(cache)
        assertTrue(second.isFile)
        assertTrue(second.parentFile.isDirectory)
    }

    @Test fun repeatedScansNeverReuseTheSameFilename(){
        val cache=temp.newFolder("cache")
        val first=ScanOutputFiles.newScan(cache)
        val second=ScanOutputFiles.newScan(cache)
        assertNotEquals(first.canonicalPath,second.canonicalPath)
    }

    @Test fun blockedCameraDirectoryFailsWithReadableError(){
        val cache=temp.newFolder("cache")
        File(cache,"camera").writeText("not a directory")
        try {
            ScanOutputFiles.newScan(cache)
            fail("Expected a clear IO error")
        }catch(e:IOException){
            assertTrue(e.message.orEmpty().contains("directory"))
        }
    }

    @Test fun saveEnsuresExistingOutputParentIsRecreated(){
        val cache=temp.newFolder("cache")
        val output=File(cache,"camera/page.jpg")
        ScanOutputFiles.ensureParent(output)
        assertTrue(output.parentFile.isDirectory)
        output.parentFile.deleteRecursively()
        ScanOutputFiles.ensureParent(output)
        assertTrue(output.parentFile.isDirectory)
    }
}
