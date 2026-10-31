package com.snehith.documentvault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Retained through activity rotation, independent from Compose's UI coroutine scope. */
data class VaultJobResult(val note:String="",val cardId:String?=null,
    val destination:String?=null,val continueScan:Boolean=false,
    val clearCrop:Boolean=false,val externalPath:String?=null)
data class VaultJobState(val busy:String="",val error:String?=null,
    val result:VaultJobResult?=null,val sequence:Long=0)
class VaultOperationModel:ViewModel(){
    private val mutable=MutableStateFlow(VaultJobState())
    val state:StateFlow<VaultJobState> = mutable
    fun start(title:String,operation:suspend()->VaultJobResult):Boolean {
        if(mutable.value.busy.isNotBlank())return false
        val ticket=mutable.value.sequence+1
        mutable.value=VaultJobState(busy=title,sequence=ticket)
        viewModelScope.launch {
            try{
                val result=withContext(Dispatchers.IO){operation()}
                mutable.value=VaultJobState(result=result,sequence=ticket)
            }catch(cancelled:CancellationException){throw cancelled}
            catch(e:Exception){mutable.value=VaultJobState(error=e.message?:"Operation failed",
                sequence=ticket)}
        }
        return true
    }
    fun acknowledge(sequence:Long) {
        if(mutable.value.sequence==sequence&&mutable.value.busy.isBlank()){
            mutable.value=mutable.value.copy(error=null,result=null)
        }
    }
}
