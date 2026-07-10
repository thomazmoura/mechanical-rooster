using Google.Apis.Auth;

namespace RelentlessBadger.Api.Auth;

public record GoogleIdentity(string Sub, string Email, string? Name);

public interface IGoogleTokenValidator
{
    Task<GoogleIdentity?> ValidateAsync(string idToken);
}

public class GoogleTokenValidator(IConfiguration configuration, ILogger<GoogleTokenValidator> logger)
    : IGoogleTokenValidator
{
    public async Task<GoogleIdentity?> ValidateAsync(string idToken)
    {
        // Dev bypass: lets you exercise the API with curl without a real Google
        // token. Only honored when explicitly configured (appsettings.Development.json).
        var bypassToken = configuration["Auth:DevBypassToken"];
        if (!string.IsNullOrEmpty(bypassToken) && idToken == bypassToken)
        {
            return new GoogleIdentity("dev-google-sub", "dev@example.com", "Dev User");
        }

        var audience = configuration["Auth:GoogleClientId"];
        if (string.IsNullOrEmpty(audience))
        {
            throw new InvalidOperationException(
                "Auth:GoogleClientId is not configured. Set it to your Google OAuth Web client ID.");
        }

        try
        {
            var payload = await GoogleJsonWebSignature.ValidateAsync(idToken,
                new GoogleJsonWebSignature.ValidationSettings { Audience = [audience] });
            return new GoogleIdentity(payload.Subject, payload.Email, payload.Name);
        }
        catch (InvalidJwtException ex)
        {
            logger.LogWarning(ex, "Rejected invalid Google ID token");
            return null;
        }
    }
}
