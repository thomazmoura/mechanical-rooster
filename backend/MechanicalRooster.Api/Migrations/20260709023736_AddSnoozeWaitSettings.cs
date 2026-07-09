using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MechanicalRooster.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddSnoozeWaitSettings : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<int>(
                name: "LongWaitMinutes",
                table: "Users",
                type: "integer",
                nullable: false,
                defaultValue: 240);

            migrationBuilder.AddColumn<int>(
                name: "MediumWaitMinutes",
                table: "Users",
                type: "integer",
                nullable: false,
                defaultValue: 60);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "LongWaitMinutes",
                table: "Users");

            migrationBuilder.DropColumn(
                name: "MediumWaitMinutes",
                table: "Users");
        }
    }
}
