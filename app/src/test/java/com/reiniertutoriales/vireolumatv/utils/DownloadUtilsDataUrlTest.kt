package com.reiniertutoriales.vireolumatv.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadUtilsDataUrlTest {
    @Test
    fun extractsPayloadForTypedBlob() {
        assertEquals("SGVsbG8=", DownloadUtils.dataUrlBase64Payload("data:text/plain;base64,SGVsbG8="))
    }

    @Test
    fun extractsPayloadForBlobWithoutType() {
        //Blob.type == "" -> readAsDataURL() writes application/octet-stream
        assertEquals("SGVsbG8=", DownloadUtils.dataUrlBase64Payload("data:application/octet-stream;base64,SGVsbG8="))
    }

    @Test
    fun extractsPayloadForMimeTypeWithParameters() {
        assertEquals("SGVsbG8=", DownloadUtils.dataUrlBase64Payload("data:text/plain;charset=utf-8;base64,SGVsbG8="))
    }

    @Test
    fun emptyBlobGivesEmptyPayload() {
        assertEquals("", DownloadUtils.dataUrlBase64Payload("data:application/octet-stream;base64,"))
    }

    @Test
    fun inputWithoutMarkerIsReturnedUnchanged() {
        assertEquals("SGVsbG8=", DownloadUtils.dataUrlBase64Payload("SGVsbG8="))
    }
}
