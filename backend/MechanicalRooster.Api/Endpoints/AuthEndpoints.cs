using MechanicalRooster.Api.Auth;
using MechanicalRooster.Api.Contracts;
using MechanicalRooster.Api.Data;
using MechanicalRooster.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace MechanicalRooster.Api.Endpoints;

public static class AuthEndpoints
{
    public static void MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        app.MapPost("/auth/google", async (
            GoogleLoginRequest request,
            IGoogleTokenValidator googleValidator,
            JwtIssuer jwtIssuer,
            AppDbContext db) =>
        {
            var identity = await googleValidator.ValidateAsync(request.IdToken);
            if (identity is null)
            {
                return Results.Unauthorized();
            }

            var user = await db.Users.SingleOrDefaultAsync(u => u.GoogleSub == identity.Sub);
            if (user is null)
            {
                user = new User
                {
                    Id = Guid.NewGuid(),
                    GoogleSub = identity.Sub,
                    Email = identity.Email,
                    Name = identity.Name,
                };
                db.Users.Add(user);
            }
            else
            {
                user.Email = identity.Email;
                user.Name = identity.Name;
            }

            await db.SaveChangesAsync();

            var token = jwtIssuer.IssueToken(user);
            return Results.Ok(new LoginResponse(token, user.Email, user.Name, SettingsDto.From(user)));
        });
    }
}
