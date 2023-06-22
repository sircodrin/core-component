package dot.cpp.core.services;

import static dot.cpp.core.helpers.ValidationHelper.isEmpty;

import com.password4j.Argon2Function;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Argon2;
import com.typesafe.config.Config;
import dot.cpp.core.enums.ErrorCodes;
import dot.cpp.core.enums.UserRole;
import dot.cpp.core.enums.UserStatus;
import dot.cpp.core.exceptions.BaseException;
import dot.cpp.core.models.user.entity.User;
import dot.cpp.core.models.user.repository.UserRepository;
import dot.cpp.core.models.user.request.AcceptInviteRequest;
import dot.cpp.core.models.user.request.SetPasswordRequest;
import dot.cpp.core.models.user.request.UserRequest;
import java.util.List;
import java.util.UUID;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class UserService extends EntityService<User, UserRequest> {

  private static final String TEMPORARY = "temporary";
  private static final String RESET_PASSWORD_UUID = "resetPasswordUuid";
  public static final String SYSTEM = "system";
  private final Logger logger = LoggerFactory.getLogger(getClass());
  private final String passwordPepper;
  private final Argon2Function argon2 = Argon2Function.getInstance(1000, 4, 2, 32, Argon2.ID, 19);

  @Inject
  public UserService(UserRepository userRepository, Config config) {
    super(userRepository, config);
    this.passwordPepper = config.getString("password.pepper");
  }

  @Override
  public void setEntityFromRequest(User entity, UserRequest request) throws BaseException {
    if (isEmpty(entity.getPassword())) {
      entity.setPassword("temp123456789");
    }
    super.setEntityFromRequest(entity, request);
  }

  @Override
  public User getNewEntity() {
    return new User();
  }

  @Override
  public UserRequest getNewRequest() {
    return new UserRequest();
  }

  @Override
  protected BaseException notFoundException() {
    return new BaseException(ErrorCodes.USER_NOT_FOUND.getCode());
  }

  @Override
  protected UserRepository getRepository() {
    return (UserRepository) super.getRepository();
  }

  public User acceptInvitation(AcceptInviteRequest request, String resetPasswordUuid)
      throws BaseException {
    logger.debug("{}\n{}", request, resetPasswordUuid);

    final var user = findByField(RESET_PASSWORD_UUID, resetPasswordUuid);

    final var hashedPassword = getHashedPassword(request.getPassword());

    user.setPassword(hashedPassword.getResult());
    user.setUserName(request.getUsername());
    user.setFullName(request.getFullName());
    user.setIdNumber(request.getDocumentId());
    user.setResetPasswordUuid("");
    user.setStatus(UserStatus.ACTIVE);

    user.setModifiedComment("Accept invite");

    logger.debug("{}", user);
    return saveWithHistory(user, SYSTEM);
  }

  public User setPassword(SetPasswordRequest request, String resetPasswordUuid)
      throws BaseException {
    logger.debug("set password request {} with uuid {}", request, resetPasswordUuid);
    final var user = findByField(RESET_PASSWORD_UUID, resetPasswordUuid);
    final var hashedPassword = getHashedPassword(request.getPassword());
    user.setPassword(hashedPassword.getResult());
    user.setResetPasswordUuid("");
    user.setModifiedComment("Set password");

    return saveWithHistory(user, SYSTEM);
  }

  public String generateResetPasswordUuid(String email) throws BaseException {
    final User user = findByField("email", email);
    if (!user.isActive()) {
      throw new BaseException(ErrorCodes.USER_INACTIVE_ACCOUNT.getCode());
    }

    final String resetPasswordUuid = UUID.randomUUID().toString();
    user.setResetPasswordUuid(resetPasswordUuid);
    user.setModifiedComment("Reset password");
    saveWithHistory(user, SYSTEM);

    logger.debug("{}", user);
    return resetPasswordUuid;
  }

  public boolean checkPassword(String hashedPassword, String password) {
    boolean verified =
        Password.check(password, hashedPassword).addPepper(passwordPepper).with(argon2);
    logger.debug("verified {}", verified);
    return verified;
  }

  public User userIsActiveAndHasRole(String userId, List<UserRole> userRoles) throws BaseException {

    final var user = findById(userId);
    logger.debug("{}", user);

    if (!user.isActive()) {
      throw new BaseException(ErrorCodes.USER_INACTIVE_ACCOUNT.getCode());
    }
    if (!userRoles.isEmpty() && !userRoles.contains(user.getRole())) {
      throw new BaseException(ErrorCodes.USER_ROLE_MISMATCH.getCode());
    }

    return user;
  }

  private Hash getHashedPassword(String password) {
    return Password.hash(password).addRandomSalt(16).addPepper(passwordPepper).with(argon2);
  }

  public User createTestUser(String email, UserRole userRole) {
    final var user = new User();
    final var resetPasswordUuid = UUID.randomUUID().toString();

    user.setEmail(email);
    user.setRole(userRole);
    user.setUserName(TEMPORARY);
    user.setPassword(TEMPORARY);
    user.setStatus(UserStatus.INACTIVE);
    user.setResetPasswordUuid(resetPasswordUuid);
    user.setFullName(TEMPORARY);
    user.setIdNumber(TEMPORARY);

    return save(user);
  }
}
