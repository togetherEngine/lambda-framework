package org.lambda.framework.sub.openai.service.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.lambda.framework.common.exception.EventException;
import org.lambda.framework.redis.operation.ReactiveRedisOperation;
import org.lambda.framework.sub.openai.OpenAiContract;
import org.lambda.framework.sub.openai.OpenAiConversation;
import org.lambda.framework.sub.openai.OpenAiConversations;
import org.lambda.framework.sub.openai.OpenAiReplying;
import org.lambda.framework.sub.openai.service.chat.param.OpenAiChatParam;
import org.lambda.framework.sub.openai.service.chat.response.OpenAiChatReplied;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;

import static org.lambda.framework.common.enums.ExceptionEnum.*;
import static org.lambda.framework.sub.openai.OpenAiContract.*;
import static org.lambda.framework.sub.openai.OpenAiModelEnum.TURBO;

@Component
public class OpenAiChatService implements OpenAiChatFunction {

    @Resource(name = "openAiChatRedisTemplate")
    private ReactiveRedisTemplate openAiChatRedisTemplate;

    @Override
    public Mono<OpenAiReplying<OpenAiChatReplied>> execute(OpenAiChatParam param) {
        //参数校验
        param.verify();

        String uniqueId = OpenAiContract.uniqueId(param.getUserId(),param.getUniqueParam().getUniqueTime());

        return ReactiveRedisOperation.build(openAiChatRedisTemplate).get(uniqueId)
                .onErrorResume(e->Mono.error(new EventException(EAI00000007)))
                .defaultIfEmpty(Mono.empty())
                .flatMap(e->{
                    List<ChatMessage> chatMessage = null;
                    List<OpenAiChatReplied> openAiChatReplied = null;
                    List<OpenAiConversation<OpenAiChatReplied>> openAiConversation = null;
                    OpenAiConversations<OpenAiChatReplied> openAiConversations = null;
                    try {
                        Integer tokens = 0;
                        if(e.equals(Mono.empty())){
                            //没有历史聊天记录,第一次对话,装载AI人设
                            chatMessage = new LinkedList<>();
                            openAiChatReplied = new LinkedList<>();
                            if(StringUtils.isNotBlank(param.getPersona())){
                                chatMessage.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),param.getPersona()));
                                openAiChatReplied.add(new OpenAiChatReplied(ChatMessageRole.SYSTEM.value(),param.getPersona(),currentTime()));
                                tokens = tokens + encoding(param.getPersona());
                            }
                            chatMessage.add(new ChatMessage(ChatMessageRole.USER.value(),param.getPrompt()));
                            openAiChatReplied.add(new OpenAiChatReplied(ChatMessageRole.USER.value(),param.getPrompt(),currentTime()));
                            tokens = tokens + encoding(param.getPrompt());
                            openAiConversation = new LinkedList<>();
                            OpenAiConversation<OpenAiChatReplied> _openAiConversation = new OpenAiConversation<OpenAiChatReplied>();
                            _openAiConversation.setConversation(openAiChatReplied);
                            openAiConversation.add(_openAiConversation);
                            openAiConversations = new OpenAiConversations<OpenAiChatReplied>();
                            openAiConversations.setOpenAiConversations(openAiConversation);

                            if(!limitVerify(param.getQuota(),param.getMaxTokens(),tokens))return Mono.error(new EventException(EAI00000100));
                        }else {
                            List<ChatMessage> _chatMessage = new LinkedList<>();
                            openAiConversations = new ObjectMapper().convertValue(e, new TypeReference<>(){});
                            openAiConversations.getOpenAiConversations().forEach(_conversations->{
                                _conversations.getConversation().forEach(message->{
                                    _chatMessage.add(new ChatMessage(message.getRole(),message.getContent()));
                                });
                            });
                            chatMessage = _chatMessage;
                            chatMessage.add(new ChatMessage(ChatMessageRole.USER.value(),param.getPrompt()));
                            openAiChatReplied = new LinkedList<>();
                            openAiChatReplied.add(new OpenAiChatReplied(ChatMessageRole.USER.value(),param.getPrompt(),currentTime()));
                            tokens = tokens + encoding(param.getPrompt());
                            OpenAiConversation<OpenAiChatReplied> _openAiConversation = new OpenAiConversation<>();
                            _openAiConversation.setConversation(openAiChatReplied);
                            openAiConversations.getOpenAiConversations().add(_openAiConversation);
                            //多轮对话要计算所有的tokens
                            tokens = Math.toIntExact(tokens + openAiConversations.getTotalTokens());
                            if(!limitVerify(param.getQuota(),param.getMaxTokens(),tokens))return Mono.error(new EventException(EAI00000100));
                        }
                    }catch (Throwable throwable){
                        return Mono.error(new EventException(EAI00000006,throwable.getMessage()));
                    }

                    try {
                        OpenAiService service = new OpenAiService(param.getApiKey(),Duration.ofSeconds(param.getTimeOut()));
                        ChatCompletionRequest request = ChatCompletionRequest.builder()
                                .model(TURBO.getModel())
                                .messages(chatMessage)
                                .temperature(param.getTemperature())
                                .topP(param.getTopP())
                                .n(param.getN())
                                .stream(param.getStream())
                                .maxTokens(param.getMaxTokens())
                                .presencePenalty(param.getPresencePenalty())
                                .frequencyPenalty(param.getFrequencyPenalty())
                                .build();
                        ChatCompletionResult chatCompletionResult = service.createChatCompletion(request);
                        ChatMessage _chatMessage = chatCompletionResult.getChoices().get(0).getMessage();
                        OpenAiConversation<OpenAiChatReplied> _openAiConversation = openAiConversations.getOpenAiConversations().get(openAiConversations.getOpenAiConversations().size()-1);
                        _openAiConversation.setPromptTokens(chatCompletionResult.getUsage().getPromptTokens());
                        _openAiConversation.setCompletionTokens(chatCompletionResult.getUsage().getCompletionTokens());
                        _openAiConversation.setTotalTokens(chatCompletionResult.getUsage().getTotalTokens());
                        _openAiConversation.getConversation().add(new OpenAiChatReplied(_chatMessage.getRole(),_chatMessage.getContent(), OpenAiContract.currentTime()));

                        openAiConversations.setTotalTokens(openAiConversations.getTotalTokens() + chatCompletionResult.getUsage().getTotalTokens());
                        openAiConversations.setTotalPromptTokens(openAiConversations.getTotalPromptTokens() + chatCompletionResult.getUsage().getPromptTokens());
                        openAiConversations.setTotalCompletionTokens(openAiConversations.getTotalCompletionTokens() + chatCompletionResult.getUsage().getCompletionTokens());

                        ReactiveRedisOperation.build(openAiChatRedisTemplate).set(uniqueId, openAiConversations);

                        return Mono.just(_openAiConversation).flatMap(current->{
                            OpenAiReplying<OpenAiChatReplied> openAiReplying =  new OpenAiReplying<OpenAiChatReplied>();
                            openAiReplying.setReplying(current.getConversation().get(current.getConversation().size()-1));
                            openAiReplying.setPromptTokens(current.getPromptTokens());
                            openAiReplying.setCompletionTokens(current.getCompletionTokens());
                            openAiReplying.setTotalTokens(current.getTotalTokens());
                            return Mono.just(openAiReplying);
                        });
                    }catch (Throwable throwable){
                        return Mono.error(new EventException(EAI00000006,throwable.getMessage()));
                    }
                });
    }

}
