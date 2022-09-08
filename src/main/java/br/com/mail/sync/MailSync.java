package br.com.mail.sync;

import br.com.cifarma.entity.TwebschedulsTO;
import br.com.cifarma.entity.TwebusuariosTO;
import br.com.cifarma.entity.business.controller.business.interfaces.Twebscheduls;
import br.com.cifarma.entity.enumerator.TwebschedulsStatus;
import br.com.core.exception.NegocioException;
import br.com.core.util.CriptografiaUtil;
import br.com.core.util.DateUtil;
import br.com.core.util.LoggedUser;
import br.com.core.util.ProxyUtil;
import br.com.gmail.entity.TwebprofilesTO;
import br.com.gmail.entity.TwebpubsubTO;
import br.com.gmail.entity.business.controller.bussiness.interfaces.Tweblabels;
import br.com.gmail.entity.business.controller.bussiness.interfaces.Twebmessages;
import br.com.gmail.entity.business.controller.bussiness.interfaces.Twebprofiles;
import br.com.gmail.entity.business.controller.bussiness.interfaces.Twebprofiles.TwebprofilesSincronizacao;
import br.com.gmail.entity.business.controller.bussiness.interfaces.Twebpubsub;
import br.com.mcp.val.sec.business.controller.business.interfaces.Twebpreferencias;
import br.com.mcp.val.sec.entity.TwebpreferenciasTO;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.History;
import com.google.api.services.gmail.model.HistoryLabelAdded;
import com.google.api.services.gmail.model.HistoryLabelRemoved;
import com.google.api.services.gmail.model.HistoryMessageAdded;
import com.google.api.services.gmail.model.HistoryMessageDeleted;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.common.flogger.FluentLogger;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import net.redhogs.cronparser.CronExpressionDescriptor;
import net.redhogs.cronparser.Options;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * <b>Classe:</b> MailSync <br>
 * <b>Descrição:</b>     <br>
 *
 * <b>Projeto:</b> mail-sync <br>
 * <b>Pacote:</b> br.com.mail.sync <br>
 * <b>Empresa:</b> Cifarma - Científica Farmacêutica LTDA. <br>
 *
 * Copyright (c) 2020 CIFARMA - Todos os direitos reservados.
 *
 * @author marcelocaser
 * @version Revision: $$ Date: 16/04/2020
 */
@Component
public class MailSync implements ApplicationRunner {

    @Autowired
    ApplicationContext applicationContext;
    @Autowired
    Twebprofiles profilesNegocio;
    @Autowired
    Twebmessages messagesNegocio;
    @Autowired
    Tweblabels labelsNegocio;
    @Autowired
    Twebpubsub pubSubNegocio;
    @Autowired
    Twebpreferencias preferenciasNegocio;
    @Autowired
    Twebscheduls schedulerNegocio;
    
    private TwebusuariosTO twebusuariosTO;

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    private static final String APPLICATION_NAME = "mail-sync-cifarma";
    //private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    
    @Bean
    public void setProxy() {
        try {
            twebusuariosTO = new TwebusuariosTO();
            twebusuariosTO.setUsuarioProxy("marcelo.2544");
            twebusuariosTO.setSenhaProxy(CriptografiaUtil.decryptBase64("VGhUu/3JcUv2YUlOSOwYzg=="));
        } catch (NegocioException ex) {
            logger.atSevere().log(ex.getMessage());
        }
    }

    @Bean
    public TwebschedulsTO getCronMailSyncFull() {
        try {
            TwebschedulsTO enableScheduler = schedulerNegocio.consultar(new TwebschedulsTO(null, "mail.sync.full", null, null));
            logger.atSevere().log("%s schedule %s with cron '%s'", buscaStatusScheduler(enableScheduler), enableScheduler.getNome(),
                    CronExpressionDescriptor.getDescription(enableScheduler.getCron(), Options.twentyFourHour()));
            return enableScheduler;
        } catch (NullPointerException ex) {
            logger.atSevere().log("Failed to read schedule task");
        } catch (ParseException ex) {
            logger.atSevere().log(ex.getMessage());
        }
        return null;
    }

    @Scheduled(cron = "#{@getCronMailSyncFull.getCron()}")
    final void syncMailFull() {
        try {
            if (TwebschedulsStatus.ATIVO.getStatusToChar().equals(getCronMailSyncFull().getAtivo())) {
                LoggedUser.logIn("MONITOR MAIL");
                logger.atInfo().log("Searching system preferences...");
                TwebpreferenciasTO twebpreferenciasTO = preferenciasNegocio.consultar();
                List<TwebprofilesTO> twebprofilesTOs = profilesNegocio.listarContasParaSincronizacaoFull();
                logger.atInfo().log("Reading %s accounts at FULL sync...", twebprofilesTOs.size());
                if (twebpreferenciasTO != null && Twebpreferencias.TwebpreferenciasStatus.SIM.getStatusToChar().equals(twebpreferenciasTO.getUtilizaProxy())) {
                    new ProxyUtil()
                            .host("10.5.100.10")
                            .port("3130")
                            .user(twebusuariosTO.getUsuarioProxy())
                            .password(twebusuariosTO.getSenhaProxy())
                            .noProxyHosts(twebpreferenciasTO.getNaoUsarServidorProxy())
                            .authenticate()
                            .noSSL();
                }
                // Build a new authorized API client service.
                final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
                for (TwebprofilesTO twebprofilesTO : twebprofilesTOs) {
                    try {
                        String credentialFile = TOKENS_DIRECTORY_PATH.concat("/").concat(twebprofilesTO.getEmail().concat("/"));
                        profilesNegocio.loadCredentials(twebprofilesTO, Paths.get(credentialFile));
                        Credential credential = profilesNegocio.getCredentials(twebprofilesTO, Paths.get(credentialFile), HTTP_TRANSPORT);
                        Gmail service = profilesNegocio.getService(credential, HTTP_TRANSPORT, APPLICATION_NAME);
                        // update profile 
                        profilesNegocio.alterar(twebprofilesTO, profilesNegocio.getProfile(service));
                        twebprofilesTO = profilesNegocio.consultar(new TwebprofilesTO(twebprofilesTO.getId()));
                        // incluir labels do profile em questao
                        List<Label> labels = labelsNegocio.listLabels(service);
                        for (Label label : labels) {
                            labelsNegocio.incluir(label, twebprofilesTO);
                        }
                        List<Message> messages = new ArrayList<>();
                        Date oldDate = twebprofilesTO.getMailFrom() != null ? twebprofilesTO.getMailFrom() : messagesNegocio.findOldestMessageDate(twebprofilesTO);
                        String query;
                        if (twebprofilesTO.getStatusSync() != null
                                && twebprofilesTO.getStatusSync().equals(TwebprofilesSincronizacao.ERRO.getSincronizacao())) {
                            query = "after:".concat(DateUtil.getData(oldDate, DateUtil.DATA_EN_PADRAO_TRACO));
                        } else {
                            query = "before:".concat(DateUtil.getData(oldDate, DateUtil.DATA_EN_PADRAO_TRACO));
                        }
                        logger.atInfo().log("Searching for messages at %s with query %s, please wait...", DateUtil.getData(oldDate, DateUtil.DATA_EN_PADRAO_TRACO), query);
                        messages = messagesNegocio.listMessagesMatchingQuery(service, Twebprofiles.EMAIL_ADDRESS, query);
                        logger.atInfo().log("Reading %s messages using query %s at FULL sync to %s...", messages.size(), query, twebprofilesTO.getEmail());
                        int verifyTotalMessage = 0;
                        for (Message message : messages) {
                            messagesNegocio.incluir(service, Twebprofiles.EMAIL_ADDRESS, message.getId(), twebprofilesTO);
                            verifyTotalMessage++;
                            if (verifyTotalMessage % 20 == 0) {
                                // atualiza status sincronizacao FULL
                                int totalMessage = messagesNegocio.totalRegistro(null, twebprofilesTO);
                                twebprofilesTO = profilesNegocio.consultar(new TwebprofilesTO(twebprofilesTO.getId()));
                                logger.atInfo().log("Reading %s of %s messages at FULL sync to %s...", totalMessage, twebprofilesTO.getMessagesTotal(), twebprofilesTO.getEmail());
                                // atualiza status sincronizacao FULL
                                if (totalMessage == twebprofilesTO.getMessagesTotal()) {
                                    profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, TwebprofilesSincronizacao.COMPLETA);
                                    break;
                                }
                            }
                        }
                        // altera a ultima data de recebimento de e-mail
                        twebprofilesTO.setMailFrom(new Date());
                        profilesNegocio.alterar(twebprofilesTO);
                    } catch (Exception ex) {
                        try {
                            if (ex instanceof NegocioException) {
                                ((NegocioException) ex).printFluentLog();
                            } else {
                                logger.atSevere().log(ex.getMessage());
                            }
                            twebprofilesTO = profilesNegocio.consultar(new TwebprofilesTO(twebprofilesTO.getId()));
                            profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, TwebprofilesSincronizacao.ERRO);
                        } catch (NegocioException negocioException) {
                            negocioException.printFluentLog();
                        }
                    }
                }
            }
        } catch (GeneralSecurityException | IOException | NegocioException ex) {
            logger.atSevere().log(ex.getMessage());
        } finally {
            LoggedUser.logOut();
        }
    }

    final void syncMailPartial() {
        try {
            LoggedUser.logIn("MONITOR MAIL");
            logger.atInfo().log("Searching system preferences...");
            //TwebpreferenciasTO twebpreferenciasTO = preferenciasNegocio.consultar();
            List<TwebprofilesTO> twebprofilesTOs = profilesNegocio.listarContasParaSincronizacaoPartial();
            logger.atInfo().log("Reading %s accounts at PARTIAL sync...", twebprofilesTOs.size());
            /*new ProxyUtil()
                    .host("10.5.100.10")
                    .port("3130")
                    .user("")
                    .password("")
                    .noProxyHosts(twebpreferenciasTO.getNaoUsarServidorProxy())
                    .authenticate()
                    .noSSL();*/
            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            for (TwebprofilesTO twebprofilesTO : twebprofilesTOs) {
                try {
                    String credentialFile = TOKENS_DIRECTORY_PATH.concat("/").concat(twebprofilesTO.getEmail().concat("/"));
                    profilesNegocio.loadCredentials(twebprofilesTO, Paths.get(credentialFile));
                    Credential credential = profilesNegocio.getCredentials(Paths.get(credentialFile), HTTP_TRANSPORT, null);
                    Gmail service = profilesNegocio.getService(credential, HTTP_TRANSPORT, APPLICATION_NAME);
                    logger.atInfo().log("Reading accounts %s and sync...", twebprofilesTO.getEmail());
                    List<History> historys = messagesNegocio.listHistory(service, Twebprofiles.EMAIL_ADDRESS, twebprofilesTO.getHistoryId());
                    for (History history : historys) {
                        if (history.getMessagesAdded() != null) {
                            for (HistoryMessageAdded historyMessageAdded : history.getMessagesAdded()) {
                                logger.atInfo().log("Adding message id %s", historyMessageAdded.getMessage().getId());
                                messagesNegocio.incluir(service, Twebprofiles.EMAIL_ADDRESS, historyMessageAdded.getMessage().getId(), twebprofilesTO);
                            }
                        }
                        if (history.getMessagesDeleted() != null) {
                            logger.atSevere().log("need test MessagesDeleted by history id : %s", history.getId());
                            for (HistoryMessageDeleted historyMessageDeleted : history.getMessagesDeleted()) {
                                logger.atInfo().log("Removing message id %s", historyMessageDeleted.getMessage().getId());
                                Message message = messagesNegocio.getMessage(service, Twebprofiles.EMAIL_ADDRESS, historyMessageDeleted.getMessage().getId());
                                messagesNegocio.excluir(message, twebprofilesTO);
                            }
                        }

                        if (history.getLabelsAdded() != null) {
                            for (HistoryLabelAdded historyLabelAdded : history.getLabelsAdded()) {
                                for (String labelId : historyLabelAdded.getLabelIds()) {
                                    logger.atInfo().log("Adding label %s to message id %s", labelId, historyLabelAdded.getMessage().getId());
                                    labelsNegocio.incluir(service, labelId, historyLabelAdded.getMessage().getId(), twebprofilesTO);
                                }
                            }
                        }

                        if (history.getLabelsRemoved() != null) {
                            for (HistoryLabelRemoved historyLabelRemoved : history.getLabelsRemoved()) {
                                for (String labelId : historyLabelRemoved.getLabelIds()) {
                                    logger.atInfo().log("Removing label %s to message id %s", labelId, historyLabelRemoved.getMessage().getId());
                                    labelsNegocio.excluir(labelsNegocio.getLabel(service, labelId), historyLabelRemoved.getMessage().getId(), twebprofilesTO);
                                }
                            }
                        }

                    }
                    // se houve alteracao atualiza o status
                    if (!historys.isEmpty()) {
                        // atualiza status sincronizacao PARTIAL
                        profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, TwebprofilesSincronizacao.PARCIAL);
                    }
                    // update profile
                    profilesNegocio.alterar(twebprofilesTO, profilesNegocio.getProfile(service));
                } catch (Exception ex) {
                    try {
                        if (ex instanceof NegocioException) {
                            ((NegocioException) ex).printFluentLog();
                        } else {
                            logger.atSevere().log("an error occurred when reading message from history %s the user %s with message erro %s", twebprofilesTO.getHistoryId(), twebprofilesTO.getEmail(), ex.getMessage() == null ? ex.getStackTrace() : ex.getMessage());
                        }
                        profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, TwebprofilesSincronizacao.ERRO);
                    } catch (NegocioException e) {
                        ((NegocioException) e).printFluentLog();
                    }
                }
            }
        } catch (GeneralSecurityException | IOException ex) {
            logger.atSevere().log(ex.getMessage());
        } finally {
            LoggedUser.logOut();
        }
    }

    private boolean isSyncMailWatch(String emailAddress, BigInteger historyId) throws GeneralSecurityException, IOException, NegocioException, Exception {
        //try {
        Boolean statusSyncFail = Boolean.FALSE;
        TwebprofilesTO twebprofilesTO = null;
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        //for (TwebprofilesTO gmailAccount : gmailAccounts) {
        //try {
        String credentialFile = TOKENS_DIRECTORY_PATH.concat("/").concat(emailAddress.concat("/"));
        twebprofilesTO = profilesNegocio.consultar(new TwebprofilesTO(null, emailAddress));
        if (twebprofilesTO != null) {
            profilesNegocio.loadCredentials(twebprofilesTO, Paths.get(credentialFile));
            Credential credential = profilesNegocio.getCredentials(Paths.get(credentialFile), HTTP_TRANSPORT, null);
            Gmail service = profilesNegocio.getService(credential, HTTP_TRANSPORT, APPLICATION_NAME);
            logger.atInfo().log("Reading accounts %s and sync...", twebprofilesTO.getEmail());
            List<History> historys = messagesNegocio.listHistory(service, Twebprofiles.EMAIL_ADDRESS, historyId);
            //messagesNegocio.incluir(service, emailAddress, messageId, twebprofilesTO);
            for (History history : historys) {
                LoggedUser.logIn("MONITOR MAIL");

                if (history.getMessages() != null) {
                    for (Message message : history.getMessages()) {
                        logger.atInfo().log("Adding message id %s", message.getId());
                        messagesNegocio.incluir(service, Twebprofiles.EMAIL_ADDRESS, message.getId(), twebprofilesTO);
                    }
                } else {
                    if (history.getMessagesAdded() != null) {
                        for (HistoryMessageAdded historyMessageAdded : history.getMessagesAdded()) {
                            logger.atInfo().log("Adding message id %s", historyMessageAdded.getMessage().getId());
                            messagesNegocio.incluir(service, Twebprofiles.EMAIL_ADDRESS, historyMessageAdded.getMessage().getId(), twebprofilesTO);
                        }
                    }

                    if (history.getMessagesDeleted() != null) {
                        logger.atSevere().log("need test MessagesDeleted by history id : %s", history.getId());
                        for (HistoryMessageDeleted historyMessageDeleted : history.getMessagesDeleted()) {
                            logger.atInfo().log("Removing message id %s", historyMessageDeleted.getMessage().getId());
                            Message message = messagesNegocio.getMessage(service, Twebprofiles.EMAIL_ADDRESS, historyMessageDeleted.getMessage().getId());
                            messagesNegocio.excluir(message, twebprofilesTO);
                        }
                    }

                    if (history.getLabelsAdded() != null) {
                        for (HistoryLabelAdded historyLabelAdded : history.getLabelsAdded()) {
                            for (String labelId : historyLabelAdded.getLabelIds()) {
                                logger.atInfo().log("Adding label %s to message id %s", labelId, historyLabelAdded.getMessage().getId());
                                labelsNegocio.incluir(service, labelId, historyLabelAdded.getMessage().getId(), twebprofilesTO);
                            }
                        }
                    }

                    if (history.getLabelsRemoved() != null) {
                        for (HistoryLabelRemoved historyLabelRemoved : history.getLabelsRemoved()) {
                            for (String labelId : historyLabelRemoved.getLabelIds()) {
                                logger.atInfo().log("Removing label %s to message id %s", labelId, historyLabelRemoved.getMessage().getId());
                                labelsNegocio.excluir(labelsNegocio.getLabel(service, labelId), historyLabelRemoved.getMessage().getId(), twebprofilesTO);
                            }
                        }
                    }
                }
            }
            // se houve alteracao atualiza o status
            if (!historys.isEmpty()) {
                statusSyncFail = Boolean.TRUE;
                // atualiza status sincronizacao PARTIAL
                profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, TwebprofilesSincronizacao.PULL);
            }
            // update profile
            //profilesNegocio.alterar(twebprofilesTO, profilesNegocio.getProfile(service));
        } else {
            throw new NegocioException("profile does not exist to", new String[]{emailAddress});
        }
        /*} catch (Exception ex) {
                try {
                    logger.atSevere().log("an error occurred when reading message from history %s the user %s with message erro %s", twebprofilesTO.getHistoryId(), twebprofilesTO.getEmail(), ex.getMessage() == null ? ex.getStackTrace() : ex.getMessage());
                    if (ex instanceof NegocioException) {
                        NegocioException negocioException = (NegocioException) ex;
                        for (Mensagem mensagem : negocioException.getMensagens()) {
                            logger.atSevere().log("%s - %s", TextUtil.removeIndentacao(mensagem.getMensagem()), mensagem.getArgumentos());
                        }
                    }
                    profilesNegocio.atualizaStatusSincronizacao(twebprofilesTO, Twebprofiles.TwebgmcntsSincronizacao.ERRO);
                } catch (NegocioException negocioException) {
                    for (Mensagem mensagem : negocioException.getMensagens()) {
                        logger.atSevere().log("%s - %s", mensagem.getMensagem(), mensagem.getArgumentos());
                    }
                }
            }*/
        //}
        /*} catch (GeneralSecurityException | IOException ex) {
            logger.atSevere().log(ex.getMessage());
        } finally {
            LoggedUser.logOut();
        }*/
        return statusSyncFail;
    }

    /*final void buscaEmailSemHtml() {
        try {
            LoggedUser.logIn("MONITOR MAIL");
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            TwebprofilesTO gmailAccount = new TwebprofilesTO(1);
            gmailAccount = profilesNegocio.consultar(gmailAccount);
            List<TwebmessagesTO> messagemSemHtml = messagesNegocio.listarMailWithoutHTML(gmailAccount);
            logger.atInfo().log("Reading %s without HTML...", messagemSemHtml.size());
            String credentialFile = TOKENS_DIRECTORY_PATH.concat("/").concat(gmailAccount.getEmail().concat("/"));
            profilesNegocio.loadCredentials(gmailAccount, credentialFile);
            Credential credential = profilesNegocio.getCredentials(credentialFile, HTTP_TRANSPORT);
            Gmail service = profilesNegocio.getService(credential, HTTP_TRANSPORT, APPLICATION_NAME);
            for (TwebmessagesTO message : messagemSemHtml) {
                try {
                    MimeMessage readMimeMessage = messagesNegocio.getMimeMessage(service, Twebprofiles.EMAIL_ADDRESS, message.getMessageId());
                    Message message = messagesNegocio.getMessage(service, Twebprofiles.EMAIL_ADDRESS, message.getMessageId());
                    messagesNegocio.alterar(message, readMimeMessage, gmailAccount);
                } catch (Exception ex) {
                    logger.atSevere().log(ex.getMessage());
                }
            }
            logger.atInfo().log("Finished reading message without HTML...");
        } catch (NegocioException | IOException | GeneralSecurityException ex) {
            logger.atSevere().log(ex.getMessage());
        } finally {
            LoggedUser.logOut();
        }
    }*/
    //@Scheduled(cron = "${cron.cinco.em.cinco.minutos}")
    final void watchAccounts() {
        TwebpubsubTO twebpubsubTO = new TwebpubsubTO();
        twebpubsubTO.setApplicationName(APPLICATION_NAME);
        twebpubsubTO = pubSubNegocio.consultar(twebpubsubTO);
        if (twebpubsubTO == null) {
            logger.atSevere().log("no 'Pub/Sub' found to %s", APPLICATION_NAME);
            finalizaApp();
        } else {
            String projectId = twebpubsubTO.getProjectId();
            String subscriptionId = twebpubsubTO.getSubscriptionId();
            Subscriber subscriber = null;
            if (projectId == null || subscriptionId == null) {
                logger.atSevere().log("project id %s or subscription id %s is invalid", projectId, subscriptionId);
                finalizaApp();
            } else {
                ProjectSubscriptionName subscriptionName
                        = ProjectSubscriptionName.of(projectId, subscriptionId);
                // Instantiate an asynchronous message receiver
                MessageReceiver receiver = new MessageReceiver() {
                    @Override
                    public void receiveMessage(PubsubMessage message, AckReplyConsumer consumer) {
                        try {
                            // handle incoming message, then ack/nack the received message
                            logger.atInfo().log("message id : %s", message.getMessageId());
                            logger.atInfo().log("attributes : %s", message.getAttributesMap());
                            logger.atInfo().log("data : %s", message.getData().toStringUtf8());
                            JSONObject jSONObject = new JSONObject(message.getData().toStringUtf8());
                            logger.atInfo().log("email address : %s", jSONObject.getString("emailAddress"));
                            logger.atInfo().log("history id : %s", jSONObject.getBigInteger("historyId"));
                            logger.atInfo().log("message id bytes : %s", message.getMessageIdBytes());
                            //logger.atInfo().log("publish time : %s", DateUtil.getData(message.getPublishTime().getSeconds(), DateUtil.DATA_HORA_BR_SEGUNDOS));
                            logger.atInfo().log("-----------------------------------------------------");
                            //if (isSyncMailWatch(jSONObject.getString("emailAddress"), jSONObject.getBigInteger("historyId"))) {
                            isSyncMailWatch(jSONObject.getString("emailAddress"), jSONObject.getBigInteger("historyId"));
                            logger.atInfo().log("consumer ack (OK)");
                            consumer.ack();
                            /*} else {
                            logger.atInfo().log("consumer nack (WILL BE REPEATED ON THE NEXT TIME)");
                            consumer.nack();
                            }*/
                            logger.atInfo().log("-----------------------------------------------------");
                        } catch (GeneralSecurityException | IOException | NegocioException ex) {
                            if (ex instanceof NegocioException) {
                                ((NegocioException) ex).printFluentLogIndentation();
                            } else {
                                logger.atSevere().log(ex.getMessage());
                            }
                        } catch (Exception ex) {
                            logger.atSevere().log(ex.getMessage());
                        }
                    }
                };
                // Create a subscriber for "mail-sync-cifarma" bound to the message receiver
                subscriber = Subscriber.newBuilder(subscriptionName, receiver).build();
                subscriber.startAsync().awaitRunning();
                // Allow the subscriber to run indefinitely unless an unrecoverable error occurs
                subscriber.awaitTerminated();
                // Stop receiving messages
                if (subscriber != null) {
                    subscriber.stopAsync();
                }
                LoggedUser.logOut();
            }
        }
    }

    /*final void sendMail() {
        try {
            new ProxyUtil()
                    .host("10.5.100.10")
                    .port("3130")
                    .user("marcelo.2544")
                    .password("@marcelo369")
                    .noProxyHosts("")
                    .authenticate()
                    .noSSL();
            List<TwebprofilesTO> twebprofilesTOs = profilesNegocio.listarContasParaSincronizacaoFull();
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            String credentialFile = TOKENS_DIRECTORY_PATH.concat("/").concat(twebprofilesTOs.get(1).getEmail().concat("/"));
            profilesNegocio.loadCredentials(twebprofilesTOs.get(1), Paths.get(credentialFile));
            Credential credential = profilesNegocio.getCredentials(Paths.get(credentialFile), HTTP_TRANSPORT, null);
            Gmail service = profilesNegocio.getService(credential, HTTP_TRANSPORT, APPLICATION_NAME);
            messagesNegocio.sendEmail(service, twebprofilesTOs.get(1).getEmail(), "notafiscal@cifarma.com.br");
        } catch (GeneralSecurityException | IOException | NegocioException ex) {
            Logger.getLogger(MailSync.class.getName()).log(Level.SEVERE, null, ex);
        }
    }*/
    private String buscaStatusScheduler(TwebschedulsTO twebschedulsTO) {
        return TwebschedulsStatus.ATIVO.getStatusToChar().equals(twebschedulsTO.getAtivo()) ? "Enable" : "Disable";
    }

    private void finalizaApp() {
        logger.atInfo().log("Aplicacao sera encerrada...verifique as configuracoes");
        int exitCode = SpringApplication.exit(applicationContext, (ExitCodeGenerator) () -> 0);
        System.exit(exitCode);
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        logger.atInfo().log("Application started with option names: %s", args.getSourceArgs());
        logger.atInfo().log("Call notification watch on the given user mailbox");
        watchAccounts();
    }
}
