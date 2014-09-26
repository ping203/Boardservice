/**
 * This file is part of Boardservice Software package.
 * @copyright (c) 2014 Cuong Pham-Minh
 *
 * Boardservice is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License, version 2 (GPL-2.0)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * A copy of the license can be viewed in the docs/LICENSE.txt file.
 * The same can be viewed at <http://opensource.org/licenses/gpl-2.0.php>
 */

package com.board.games.handler.ipb;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.ini4j.Ini;

import com.board.games.config.ServerConfig;
import com.board.games.handler.generic.PokerConfigHandler;
import com.board.games.service.wallet.WalletAdapter;
import com.cubeia.firebase.api.action.local.LoginRequestAction;
import com.cubeia.firebase.api.action.local.LoginResponseAction;
import com.cubeia.firebase.api.login.LoginHandler;
import com.cubeia.firebase.api.service.ServiceRouter;
import com.board.games.helper.BCrypt;

public class IPBPokerLoginServiceImpl extends PokerConfigHandler implements LoginHandler {

	private static AtomicInteger pid = new AtomicInteger(0);
	private Logger log = Logger.getLogger(this.getClass());
	private ServiceRouter router;
	private static Connection connect = null;
	private static Statement statement = null;
	// private static PreparedStatement preparedStatement = null;
	private static ResultSet resultSet = null;
	private String connectionStr = "";
	private String jdbcDriverClassName = "";
	private String dbPrefix = "";
	  private static boolean newIPB4Version = false;

	protected void initialize() {
		super.initialize();
		try {
			Ini ini = new Ini(new File("JDBCConfig.ini"));
			String jdbcDriver = ini.get("JDBCConfig", "jdbcDriver");
			String connectionUrl = ini.get("JDBCConfig", "connectionUrl");
			String database = ini.get("JDBCConfig", "database");
			String ipbVersion = "";
			dbPrefix = ini.get("JDBCConfig", "dbPrefix");
			String user = ini.get("JDBCConfig", "user");
			String password = ini.get("JDBCConfig", "password");
	/*			currency = ini.get("JDBCConfig", "currency");
			walletBankAccountId = ini.get("JDBCConfig", "walletBankAccountId");
			initialAmount = ini.get("JDBCConfig", "initialAmount");
			useIntegrations = ini.get("JDBCConfig", "useIntegrations");
			serverCfg = new ServerConfig(currency, new Long(walletBankAccountId), new BigDecimal(initialAmount), useIntegrations.equals("Y")? true:false);
	*/
			ipbVersion = ini.get("JDBCConfig", "ipbVersion");
			if (!ipbVersion.equals("") && "IPS4".equals(ipbVersion.toUpperCase())) {
				newIPB4Version = true;
				log.debug("Detecting  IPS4 versionx");
			}
			jdbcDriverClassName = ini.get("JDBCConfig", "driverClassName");
			connectionStr = "jdbc" + ":" + jdbcDriver + "://" + connectionUrl
					+ "/" + database + "?user=" + user + "&password="
					+ password;
			log.debug("User " + user);
			//log.debug("connectionStr " + connectionStr);
		} catch (IOException ioe) {
			log.error("Exception in initialize " + ioe.toString());
		} catch (Exception e) {
			log.error("Exception in initialize " + e.toString());
		}
		
	}
	@Override
	public LoginResponseAction handle(LoginRequestAction req) {
		// At this point, we should get the user name and password
		// from the request and verify them, but for this example
		// we'll just assign a dynamic player ID and grant the login
/*		String currency = "USD";
		String walletBankAccountId = "2";
		String initialAmount = "1000";
		String useIntegrations = "Y";
		ServerConfig serverCfg=null;*/
			// Must be the very first call
			initialize();			

		LoginResponseAction response = null;
		try {
			log.debug("Performing authentication on " + req.getUser());
			String userIdStr = authenticate(req.getUser(), req.getPassword(), getServerCfg());
			if (!userIdStr.equals("")) {
				
				response = new LoginResponseAction(Integer.parseInt(userIdStr) > 0?true:false, (req.getUser().toUpperCase().startsWith("GUESTXDEMO")?req.getUser()+"_"+userIdStr:req.getUser()),
						Integer.parseInt(userIdStr)); // pid.incrementAndGet()
				log.debug(Integer.parseInt(userIdStr) > 0?"Authentication successful":"Authentication failed");
				return response;
			}
		} catch (SQLException sqle) {
			log.error("Error authenticate", sqle);
			response = new LoginResponseAction(false, -1);
			response.setErrorMessage(getSystemErrorMessage(sqle));
			response.setErrorCode(getSystemErrorCode(sqle));
			log.error(sqle);
		} catch (Exception e) {
			log.error("Error system", e);
		}

		response = new LoginResponseAction(false, -1);
		response.setErrorMessage(getNotFoundErrorMessage());
		response.setErrorCode(getNotFoundErrorCode());
		return response;
	}

	/**
	 * This method should return the error code to send back if the sql query
	 * fails. Default msg is 0.
	 * 
	 * @param e
	 *            The sql exception, never null
	 * @return The system error code
	 */
	protected int getSystemErrorCode(SQLException e) {
		return 0;
	}

	/**
	 * This method should return the error message to send back if the sql query
	 * fails. Default msg is "System error."
	 * 
	 * @param e
	 *            The sql exception, never null
	 * @return The system error message
	 */
	protected String getSystemErrorMessage(SQLException e) {
		return "System error.";
	}

	/**
	 * This method should return the error code to send back if the sql query
	 * does not get any results. Default msg is 0.
	 * 
	 * @return The "user not found" error code
	 */
	protected int getNotFoundErrorCode() {
		return 0;
	}

	/**
	 * This method should return the message to send back if the sql query does
	 * not get any results. Default msg is "User not found."
	 * 
	 * @return The "user not found" error message, may be null
	 */
	protected String getNotFoundErrorMessage() {
		return "User not found or registered but at least 1 post is required to play.";
	}

	private String authenticate(String user, String password, ServerConfig serverConfig) throws Exception {
		try {
			int idx = user.indexOf("_");
			if (idx != -1) {
				// let bots through
				String idStr = user.substring(idx+1);
				if (user.toUpperCase().startsWith("BOT")) {
					if (serverConfig.isUseIntegrations()) {
						WalletAdapter walletAdapter = new WalletAdapter();
						log.debug("Calling createWalletAccount");
						//walletAdapter.createWalletAccount(new Long(String.valueOf(member_id)));
						Long userId = walletAdapter.checkCreateNewUser(idStr, user, new Long(0), serverConfig.getCurrency(), serverConfig.getWalletBankAccountId(), serverConfig.getInitialAmount());
						return String.valueOf(userId);
					} else {
						return idStr;
					}

				}
			}
			if (user.toUpperCase().startsWith("GUESTXDEMO")) {
				return String.valueOf(pid.incrementAndGet()+500000);
			}
			
			log.debug("loading class name for database connection" + jdbcDriverClassName);
			// This will load the MySQL driver, each DB has its own driver
			// "com.mysql.jdbc.Driver"
			Class.forName(jdbcDriverClassName);
			// Setup the connection with the DB
			// "jdbc:mysql://localhost/dbName?" + "user=&password=");
			connect = DriverManager.getConnection(connectionStr);

			// Statements allow to issue SQL queries to the database
			statement = connect.createStatement();
			log.debug("Execute query: authenticate");
			// Result set get the result of the SQL query
			// SELECT * FROM ipb3_members WHERE members_seo_name = ''
			String data = newIPB4Version ? "core_members " : "members ";
			String selectSQL = "select members_seo_name,  member_id, name, "
					+ " members_pass_hash,  members_pass_salt,  "
					+ " title, posts from " + dbPrefix + data
					+ " where name = " + "\'" + user + "\'";
			
				
			log.debug("Executing query : " + selectSQL);
			resultSet = statement.executeQuery(selectSQL);
			String checkPwdHash = null;
			String checkPwdHashNew = null;
			String members_pass_hash = null;
			int member_id = 0;
			int posts = 0;
			boolean authenticated = false;
			if (resultSet != null && resultSet.next()) {
				String members_seo_name = resultSet
						.getString("members_seo_name");
				member_id = resultSet.getInt("member_id");
				String name = resultSet.getString("name");
				members_pass_hash = resultSet.getString("members_pass_hash");
				
				log.debug("DB members_pass_hash = " + members_pass_hash);
				
				String members_pass_salt = resultSet
						.getString("members_pass_salt");
				//String members_display_name = resultSet
				//		.getString("members_display_name");
				String title = resultSet.getString("title");
				posts = resultSet.getInt("posts");
				log.debug("User: " + user + " Password " + "**********");
				
				String escapePwdHTML = StringEscapeUtils.escapeHtml(password);
				log.debug("escapeHTML = " + escapePwdHTML);
				
				if (!newIPB4Version) {
					String pwdMD5 = getMD5(password);
					String pwdMD5New = getMD5New(escapePwdHTML);
	
					log.debug("pwdMD5 = " + pwdMD5);
					log.debug("pwdMD5New = " + pwdMD5New);
					
					
					String pwdSaltMD5 = getMD5(members_pass_salt);
					if (pwdSaltMD5 == null)
						log.debug("pwdMD5 is null");
					
					String pwdSaltMD5New = getMD5New(members_pass_salt);
	
					log.debug("pwdSaltMD5 = " + pwdSaltMD5);
					log.debug("pwdSaltMD5New = " + pwdSaltMD5New);
					
					if (pwdMD5 != null) {
						checkPwdHash = getMD5(pwdSaltMD5 + pwdMD5);
						log.debug("checkPwdHash = " + checkPwdHash);
					}
					else
						log.debug("pwdMD5 is null");
					
					if (pwdMD5New != null) {
						checkPwdHashNew = getMD5New(pwdSaltMD5New + pwdMD5New);
						log.debug("checkPwdHashNew = " + checkPwdHashNew);
					}
					else
						log.debug("pwdMD5New is null");
					
					if (checkPwdHash != null && members_pass_hash != null) {
						if (checkPwdHash.equals(members_pass_hash)) {
							authenticated = true;
						}
					}
				} else {
					authenticated = BCrypt.checkpw(escapePwdHTML,members_pass_hash);
										
				}
				log.debug("members_pass_hash = " + members_pass_hash);
				log.debug("# of Post " + posts);
				
				if (authenticated) {
					if (serverConfig != null) {	
					if (serverConfig.isUseIntegrations()) {
						
						WalletAdapter walletAdapter = new WalletAdapter();
						log.debug("Calling createWalletAccount");
						//walletAdapter.createWalletAccount(new Long(String.valueOf(member_id)));
						Long userId = walletAdapter.checkCreateNewUser(String.valueOf(member_id), members_seo_name, new Long(1), serverConfig.getCurrency(), serverConfig.getWalletBankAccountId(), serverConfig.getInitialAmount());
						log.debug("assigned new id as #" + String.valueOf(userId));
						return String.valueOf(userId);
/*						if (posts >= 1) {
								return String.valueOf(member_id);
							} else {
								log.error("Required number of posts not met, denied login");
								return "-2";
							}
*/
					} else {
						return String.valueOf(member_id);
					}
					
				} else {
					log.error("ServerConfig is null.");
				}						
						} else {
					log.error("Authenticated failed: hash not matched for user " + user + " password " + password);
					return "-1";
				}

				
			} else {
				log.error("resultset is null " + selectSQL);
			}
			

		} catch (Exception e) {
			log.error("Error : " + e.toString());
			// throw e;
		} finally {
			close();
		}
		return "-3";
	}



	private void close() {
		try {
			if (resultSet != null) {
				resultSet.close();
			}

			if (statement != null) {
				statement.close();
			}

			if (connect != null) {
				connect.close();
			}
		} catch (Exception e) {

		}
	}

	private synchronized String getMD5(String input) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] messageDigest = md.digest(input.getBytes("UTF-8"));
			BigInteger number = new BigInteger(1, messageDigest);
			String hashtext = number.toString(16);
			// Now we need to zero pad it if you actually want the full 32
			// chars.
			while (hashtext.length() < 32) {
				hashtext = "0" + hashtext;
			}
			return hashtext;
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException(e);
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        return null;
	}

	
		private  synchronized String getMD5New(String input) {
	        // please note that we dont use digest, because if we
	        // cannot get digest, then the second time we have to call it
	        // again, which will fail again
	        MessageDigest digest = null;

	        try {
	            digest = MessageDigest.getInstance("MD5");
	        } catch (Exception ex) {
	            ex.printStackTrace();
	        }

	        if (digest == null)
	            return input;

	        // now everything is ok, go ahead
	        try {
	            digest.update(input.getBytes("UTF-8"));
	        } catch (java.io.UnsupportedEncodingException ex) {
	            ex.printStackTrace();
	        }
	        
	        
	        final StringBuilder sbMd5Hash = new StringBuilder();

	        final byte data[] = digest.digest();

/*	        for (byte element : data) {
	        sbMd5Hash.append(Character.forDigit((element >> 4) & 0xf, 16));
	        sbMd5Hash.append(Character.forDigit(element & 0xf, 16));
	        }
$%6e!df fek@&^$345M
pkrGlr1Test
	        
	        return sbMd5Hash.toString();
*/	        //final byte[] md5Digest = md.digest(password.getBytes());

	        final BigInteger md5Number = new BigInteger(1, data);
	        final String md5String = md5Number.toString(16);
	        return md5String;
	        
		}
	
}