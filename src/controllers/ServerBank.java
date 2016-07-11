package controllers;

import models.Account;
import models.Bank;
import models.MessageAlertTag;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;

import java.util.Hashtable;

/**
 * Controller that makes the comunications between the UserScreen view
 * and the Bank model
 *
 * @author Bruno Tomé
 * @author Cláudio Menezes
 * @since 03/07/2016
 */
public class ServerBank extends ReceiverAdapter {

    private Bank BCBank = new Bank();
    private JChannel channel;

    public ServerBank() throws Exception {
        this.start();
    }

    /**
     * Try to login into the system
     *
     * @param a
     * @return Account
     */
    public Account login(Account a) {
        Account accountAux = this.BCBank.getAllAccounts().get(a.getAccountNumber());
        if (accountAux != null && accountAux.getPassword().equals(a.getPassword())) {
            return accountAux;
        } else {
            return null;
        }
    }

    /**
     * Transfer an amount of cash between two accounts
     *
     * @param byUser
     * @param toUser
     * @param amount
     */
    public MessageAlertTag transference(Account byUser, int toUser, Double amount) {
        Account toUserAux = this.BCBank.getAllAccounts().get(toUser);
        if (toUserAux != null && byUser.getAccountNumber() != toUserAux.getAccountNumber()) {
            if (byUser.getBalance() >= amount && amount > 0) {
                this.BCBank.transference(byUser, toUserAux, amount);
                // Add a new transfer to extract of byUser
                byUser.addToExtract("\n----------------------------\nSAÍDA\n"
                        + "----------------------------\nPara a conta nº "
                        + toUser + "\nValor: R$ " + amount + "\nMeu novo saldo: R$ "
                        + (byUser.getBalance()) + "\n----------------------------\n");
                // Add a new transfer to extract of toUser
                toUserAux.addToExtract("\n----------------------------\nENTRADA\n"
                        + "----------------------------\nProvindo da conta nº "
                        + byUser.getAccountNumber() + "\nValor: R$ " + amount + "\nMeu novo saldo: R$ "
                        + (toUserAux.getBalance()) + "\n----------------------------\n");

                // Update the allAccounts of BCBank
                Hashtable<Integer, Account> allAccounts;
                allAccounts = this.BCBank.getAllAccounts();
                allAccounts.replace(byUser.getAccountNumber(), byUser);
                allAccounts.replace(toUserAux.getAccountNumber(), toUserAux);
                this.BCBank.setAllAccounts(allAccounts);
                this.BCBank.saveState();
                return MessageAlertTag.TRANSFER_SUCCESSFUL;
            } else if (amount <= 0) {
                return MessageAlertTag.TRANSFER_ERROR_NEGATIVE;
            } else {
                return MessageAlertTag.TRANSFER_ERROR_AMOUNT;
            }
        } else if (toUserAux != null && byUser.getAccountNumber() == toUserAux.getAccountNumber()) {
            return MessageAlertTag.TRANSFER_ERROR_SAME_ACCOUNT;
        } else {
            return MessageAlertTag.TRANSFER_ERROR_ACCOUNT;
        }
    }

    /**
     * Create a new account if there isn't an equal account created yet
     *
     * @param newUser
     * @return MessageAlertTag
     */

    public MessageAlertTag signUp(Account newUser) {
        if (this.BCBank.getAllAccounts().get(newUser.getAccountNumber()) == null) {
            this.BCBank.addAccount(newUser);
            return MessageAlertTag.SIGNUP_SUCCESSFUL;
        } else {
            return MessageAlertTag.SIGNUP_ERROR;
        }
    }

    /**
     * Get the balance of an account
     *
     * @param a
     * @return String
     */
    public String getBalance(Account a) {
        return this.BCBank.getBalance(a);
    }

    @Override
    public String toString() {
        return this.BCBank.sumBankCash();
    }

    /******************************************************************************************
     * Trying to make the distributed functions
     *****************************************************************************************/

    public void receive(Message message) {
        Account account = (Account) message.getObject();
        switch (account.getTag()) {
            case TRANSFER:
                break;
            case LOGIN:
                account = this.login(account);
                message.setObject(account);
                try {
                    this.channel.send(message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                break;
            case BALANCE:
                break;
            case EXTRACT:
                break;
            default:
                break;
        }
    }

    private void start() throws Exception {
        this.channel = new JChannel("xml-configs/udp.xml");        //usa a configuração default
        this.channel.setReceiver(this); //quem irá lidar com as mensagens recebidas
        this.channel.connect("BCBankGroup");
        //eventLoop();
        this.channel.close();
    }
}
