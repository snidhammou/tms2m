package com.tms.agent.net;

import okhttp3.MultipartBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Streaming;
import retrofit2.http.Url;

public interface TmsApi {

    @POST("api/device/v1/enroll")
    Call<Dtos.EnrollResponse> enroll(@Body Dtos.EnrollRequest request);

    @POST("api/device/v1/heartbeat")
    Call<Dtos.HeartbeatResponse> heartbeat(@Body Dtos.HeartbeatRequest request);

    @POST("api/device/v1/tasks/{id}/status")
    Call<Void> updateTaskStatus(@Path("id") long taskId, @Body Dtos.TaskStatusUpdate update);

    /** Fichier produit par une tâche (logs, fichier extrait). */
    @Multipart
    @POST("api/device/v1/tasks/{id}/artifact")
    Call<Void> uploadArtifact(@Path("id") long taskId, @Part MultipartBody.Part file);

    @GET("api/device/v1/parameters")
    Call<Dtos.ParametersResponse> parameters(@Query("packageName") String packageName);

    @Streaming
    @GET
    Call<ResponseBody> download(@Url String url);
}
