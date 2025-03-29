package com.example.wallet.service;

import com.example.wallet.dto.QRCodeData;
import com.example.wallet.dto.request.TransferRequestDTO;
import com.example.wallet.exception.customException.client.InvalidInteractionException;
import com.example.wallet.model.entity.Tranche;
import com.example.wallet.model.entity.Wallet;
import com.example.wallet.model.type.TrancheStatus;
import com.example.wallet.model.type.TrancheType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final QRCodeService qrCodeService;
    private final WalletService walletService;
    private final TrancheService trancheService;

    @Transactional
    public Tranche transfer(Long senderId, TransferRequestDTO transferRequest) {

        if(transferRequest.getReceiverId().equals(senderId)) {
            throw new InvalidInteractionException("you cant transfer yourself");
        }

        Wallet senderWallet = walletService.getWallet(senderId);
        Wallet receiverWallet = walletService.getWallet(transferRequest.getReceiverId());

        if (senderWallet.getBalance().compareTo(transferRequest.getAmount()) < 0) {
            throw new InvalidInteractionException("Insufficient funds, cant create a transaction");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(transferRequest.getAmount()));

        receiverWallet.setBalance(receiverWallet.getBalance().add(transferRequest.getAmount()));

        walletService.setWallet(senderWallet);
        walletService.setWallet(receiverWallet);

        Tranche tranche = Tranche.builder()
                .senderId(senderId)
                .receiverId(transferRequest.getReceiverId())
                .amount(transferRequest.getAmount())
                .createdAt(LocalDate.now())
                .currency(transferRequest.getCurrency())
                .status(TrancheStatus.PENDING)
                .type(TrancheType.TRANSFER)
                .build();
        // to kafka -> audit , anti-fraud

        return trancheService.createTranche(tranche);

    }

    @Transactional
    public Tranche QRCodeTransfer(Long senderId, String qrCodeId) {

        QRCodeData qrData = qrCodeService.validateQRCode(qrCodeId);

        if(qrData.getUserId().equals(senderId)) {
            throw new InvalidInteractionException("you cant scan your own QR!");
        }

        Wallet senderWallet = walletService.getWallet(senderId);
        Wallet receiverWallet = walletService.getWallet(qrData.getUserId());

        if (senderWallet.getBalance().compareTo(qrData.getAmount()) < 0) {
            throw new InvalidInteractionException("Insufficient funds, cant create a transaction");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(qrData.getAmount()));

        receiverWallet.setBalance(receiverWallet.getBalance().add(qrData.getAmount()));

        walletService.setWallet(senderWallet);
        walletService.setWallet(receiverWallet);

        Tranche tranche = Tranche.builder()
                .senderId(senderId)
                .receiverId(qrData.getUserId())
                .amount(qrData.getAmount())
                .createdAt(LocalDate.now())
                .currency(qrData.getCurrency())
                .status(TrancheStatus.PENDING)
                .type(TrancheType.TRANSFER)
                .build();
        // to kafka -> audit , anti-fraud

        return trancheService.createTranche(tranche);
    }
}
