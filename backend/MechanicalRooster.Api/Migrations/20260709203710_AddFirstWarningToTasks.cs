using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace MechanicalRooster.Api.Migrations
{
    /// <inheritdoc />
    public partial class AddFirstWarningToTasks : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.AddColumn<DateTime>(
                name: "FirstWarningAt",
                table: "Tasks",
                type: "timestamp with time zone",
                nullable: true);
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropColumn(
                name: "FirstWarningAt",
                table: "Tasks");
        }
    }
}
