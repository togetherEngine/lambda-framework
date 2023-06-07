package org.lambda.framework.web.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lambda.framework.common.exception.basic.GlobalException;
import org.lambda.framework.common.templete.ResponseTemplete;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.lambda.framework.web.enums.WebExceptionEnum.ES_WEB_000;


/**
 * Created by WangGang on 2017/6/22 0022.
 * E-mail userbean@outlook.com
 * The final interpretation of this procedure is owned by the author
 */
@Component
@RestControllerAdvice
@Order(-1)
@Slf4j
public class WebGlobalExceptionHandler implements ErrorWebExceptionHandler {

    private Logger logger = LoggerFactory.getLogger(WebGlobalExceptionHandler.class);

    @Override
    @ExceptionHandler(Exception.class)
    public Mono<Void> handle(ServerWebExchange serverWebExchange, Throwable throwable) {
        return write(serverWebExchange.getResponse(),handleTransferException(throwable));
    }

    private ResponseTemplete handleTransferException(Throwable e) {
        logger.error("GlobalExceptionHandle",e);
        if(e instanceof GlobalException){
            return result(((GlobalException)e).getCode(),e.getMessage());
        }
        return result(ES_WEB_000.getCode(),ES_WEB_000.getMessage());
    }

    private ResponseTemplete result(String code, String message){
        ResponseTemplete responseTemplete = new ResponseTemplete();
        responseTemplete.setServiceCode(code);
        responseTemplete.setServiceMessage(message);
        return responseTemplete;
    }



    private  <T> Mono<Void> write(ServerHttpResponse httpResponse, T object) {
        httpResponse.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        MediaType mediaType = new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8);
        httpResponse.getHeaders().setContentType(mediaType);
        return httpResponse
                .writeWith(Mono.fromSupplier(() -> {
                    DataBufferFactory bufferFactory = httpResponse.bufferFactory();
                    try {
                        return bufferFactory.wrap((new ObjectMapper()).writeValueAsBytes(object));
                    } catch (Exception ex) {
                        logger.warn("Error writing response", ex);
                        return bufferFactory.wrap(new byte[0]);
                    }
                }));
    }




}
