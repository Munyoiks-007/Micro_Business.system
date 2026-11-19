package com.example.microbusinesssystem.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Body

data class DarajaTokenResponse(
    val access_token: String? = null,
    val expires_in: String? = null
)

data class StkPushRequest(
    val BusinessShortCode: String,
    val Password: String,
    val Timestamp: String,
    val TransactionType: String = "CustomerPayBillOnline",
    val Amount: Long,
    val PartyA: String,
    val PartyB: String,
    val PhoneNumber: String,
    val CallBackURL: String,
    val AccountReference: String,
    val TransactionDesc: String
)

data class StkPushResponse(
    val MerchantRequestID: String? = null,
    val CheckoutRequestID: String? = null,
    val ResponseCode: String? = null,
    val ResponseDescription: String? = null,
    val CustomerMessage: String? = null
)

interface DarajaApiService {
    @GET("oauth/v1/generate?grant_type=client_credentials")
    suspend fun getAccessToken(
        @Header("Authorization") authorization: String
    ): Response<DarajaTokenResponse>

    @POST("mpesa/stkpush/v1/processrequest")
    suspend fun sendStkPush(
        @Header("Authorization") authorization: String,
        @Body request: StkPushRequest
    ): Response<StkPushResponse>
}
