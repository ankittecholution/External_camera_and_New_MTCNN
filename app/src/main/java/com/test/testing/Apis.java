package com.test.testing;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface Apis {

    @Multipart
    @POST("/recognise")
    Call<String> sendImage(@Part MultipartBody.Part file, @Part("camera_id") String camera_Id, @Part("faces") String faces, @Part("frame_id") String frame_id);

}
