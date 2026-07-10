using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using RelentlessBadger.Api.Models;
using Microsoft.IdentityModel.Tokens;

namespace RelentlessBadger.Api.Auth;

public class JwtIssuer(IConfiguration configuration)
{
    public string IssueToken(User user)
    {
        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(configuration["Jwt:Key"]!));
        var lifetimeDays = configuration.GetValue("Jwt:LifetimeDays", 180);

        var token = new JwtSecurityToken(
            issuer: configuration["Jwt:Issuer"],
            audience: configuration["Jwt:Audience"],
            claims:
            [
                new Claim(JwtRegisteredClaimNames.Sub, user.Id.ToString()),
                new Claim(JwtRegisteredClaimNames.Email, user.Email),
            ],
            expires: DateTime.UtcNow.AddDays(lifetimeDays),
            signingCredentials: new SigningCredentials(key, SecurityAlgorithms.HmacSha256));

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
