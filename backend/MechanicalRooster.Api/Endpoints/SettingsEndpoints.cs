using System.Security.Claims;
using MechanicalRooster.Api.Contracts;
using MechanicalRooster.Api.Data;

namespace MechanicalRooster.Api.Endpoints;

public static class SettingsEndpoints
{
    public static void MapSettingsEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/me").RequireAuthorization();

        group.MapGet("/settings", async (ClaimsPrincipal principal, AppDbContext db) =>
        {
            var user = await principal.GetUserAsync(db);
            return user is null ? Results.Unauthorized() : Results.Ok(SettingsDto.From(user));
        });

        group.MapPut("/settings", async (SettingsDto settings, ClaimsPrincipal principal, AppDbContext db) =>
        {
            if (settings.InitialDelayMinutes < 1 || settings.RepeatIntervalMinutes < 1)
            {
                return Results.BadRequest(new { error = "Delays must be at least 1 minute." });
            }

            var user = await principal.GetUserAsync(db);
            if (user is null)
            {
                return Results.Unauthorized();
            }

            user.InitialDelayMinutes = settings.InitialDelayMinutes;
            user.RepeatIntervalMinutes = settings.RepeatIntervalMinutes;
            await db.SaveChangesAsync();

            return Results.Ok(SettingsDto.From(user));
        });
    }
}
