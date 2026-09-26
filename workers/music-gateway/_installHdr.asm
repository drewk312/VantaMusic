0x14095bca0 cmp rsp, qword ptr [r14 + 0x10]
0x14095bca4 jbe 0x14095bed3
0x14095bcaa push rbp
0x14095bcab mov rbp, rsp
0x14095bcae sub rsp, 0x60
0x14095bcb2 mov qword ptr [rsp + 0x78], rbx
0x14095bcb7 mov qword ptr [rsp + 0x80], rcx
0x14095bcbf mov qword ptr [rsp + 0x88], rdi
0x14095bcc7 mov qword ptr [rsp + 0x90], rsi
0x14095bccf mov qword ptr [rsp + 0x98], r8
0x14095bcd7 mov qword ptr [rsp + 0xa0], r9
0x14095bcdf mov qword ptr [rsp + 0xa8], r10
0x14095bce7 mov qword ptr [rsp + 0xb0], r11
0x14095bcef test rax, rax
0x14095bcf2 je 0x14095becd
0x14095bcf8 mov qword ptr [rsp + 0x70], rax
0x14095bcfd mov rax, r8
0x14095bd00 mov rbx, r9
0x14095bd03 call 0x1400e40c0
0x14095bd08 test rbx, rbx
0x14095bd0b je 0x14095bddc
0x14095bd11 mov qword ptr [rsp + 0x38], rax
0x14095bd16 mov qword ptr [rsp + 0x20], rbx
0x14095bd1b mov rcx, qword ptr [rsp + 0x70]
0x14095bd20 mov rcx, qword ptr [rcx + 0x38]
0x14095bd24 mov qword ptr [rsp + 0x58], rcx
0x14095bd29 nop 
0x14095bd2a lea rax, [rip + 0xa2a7c]
0x14095bd31 mov ebx, 0x11
0x14095bd36 call 0x1402d8160
0x14095bd3b mov qword ptr [rsp + 0x50], rax
0x14095bd40 mov qword ptr [rsp + 0x30], rbx
0x14095bd45 mov eax, 0x10
0x14095bd4a lea rbx, [rip + 0x1393f8f]
0x14095bd51 mov ecx, 1
0x14095bd56 call 0x140022a00
0x14095bd5b mov rcx, qword ptr [rsp + 0x20]
0x14095bd60 mov qword ptr [rax + 8], rcx
0x14095bd64 cmp dword ptr [rip + 0x16d63e5], 0
0x14095bd6b jne 0x14095bd74
0x14095bd6d mov rdx, qword ptr [rsp + 0x38]
0x14095bd72 jmp 0x14095bd81
0x14095bd74 call 0x14008e160
0x14095bd79 mov rdx, qword ptr [rsp + 0x38]
0x14095bd7e mov qword ptr [r11], rdx
0x14095bd81 mov qword ptr [rsp + 0x48], rax
0x14095bd86 mov qword ptr [rax], rdx
0x14095bd89 lea rax, [rip + 0x14d7538]
0x14095bd90 mov rbx, qword ptr [rsp + 0x58]
0x14095bd95 mov rcx, qword ptr [rsp + 0x50]
0x14095bd9a mov rdi, qword ptr [rsp + 0x30]
0x14095bd9f nop 
0x14095bda0 call 0x14000cc80
0x14095bda5 mov qword ptr [rax + 8], 1
0x14095bdad mov qword ptr [rax + 0x10], 1
0x14095bdb5 cmp dword ptr [rip + 0x16d6394], 0
0x14095bdbc jne 0x14095bdc5
0x14095bdbe mov rdx, qword ptr [rsp + 0x48]
0x14095bdc3 jmp 0x14095bdd9
0x14095bdc5 mov rcx, qword ptr [rax]
0x14095bdc8 call 0x14008e180
0x14095bdcd mov rdx, qword ptr [rsp + 0x48]
0x14095bdd2 mov qword ptr [r11], rdx
0x14095bdd5 mov qword ptr [r11 + 8], rcx
0x14095bdd9 mov qword ptr [rax], rdx
0x14095bddc mov rax, qword ptr [rsp + 0xa8]
0x14095bde4 mov rbx, qword ptr [rsp + 0xb0]
0x14095bdec call 0x1400e40c0
0x14095bdf1 test rbx, rbx
0x14095bdf4 je 0x14095bec7
0x14095bdfa mov qword ptr [rsp + 0x40], rax
0x14095bdff mov qword ptr [rsp + 0x28], rbx
0x14095be04 mov rcx, qword ptr [rsp + 0x70]
0x14095be09 mov rcx, qword ptr [rcx + 0x38]
0x14095be0d mov qword ptr [rsp + 0x58], rcx
0x14095be12 nop 
0x14095be13 lea rax, [rip + 0x9c17c]
0x14095be1a mov ebx, 0xd
0x14095be1f nop 
0x14095be20 call 0x1402d8160
0x14095be25 mov qword ptr [rsp + 0x50], rax
0x14095be2a mov qword ptr [rsp + 0x30], rbx
0x14095be2f mov eax, 0x10
0x14095be34 lea rbx, [rip + 0x1393ea5]
0x14095be3b mov ecx, 1
0x14095be40 call 0x140022a00
0x14095be45 mov rcx, qword ptr [rsp + 0x28]
0x14095be4a mov qword ptr [rax + 8], rcx
0x14095be4e cmp dword ptr [rip + 0x16d62fb], 0
0x14095be55 jne 0x14095be60
0x14095be57 mov rdx, qword ptr [rsp + 0x40]
0x14095be5c jmp 0x14095be6d
0x14095be5e nop 
0x14095be60 call 0x14008e160
0x14095be65 mov rdx, qword ptr [rsp + 0x40]
0x14095be6a mov qword ptr [r11], rdx
0x14095be6d mov qword ptr [rsp + 0x48], rax
0x14095be72 mov qword ptr [rax], rdx
0x14095be75 lea rax, [rip + 0x14d744c]
0x14095be7c mov rbx, qword ptr [rsp + 0x58]
0x14095be81 mov rcx, qword ptr [rsp + 0x50]
0x14095be86 mov rdi, qword ptr [rsp + 0x30]
0x14095be8b call 0x14000cc80
0x14095be90 mov qword ptr [rax + 8], 1
0x14095be98 mov qword ptr [rax + 0x10], 1
0x14095bea0 cmp dword ptr [rip + 0x16d62a9], 0
0x14095bea7 jne 0x14095beb0
0x14095bea9 mov rdx, qword ptr [rsp + 0x48]
0x14095beae jmp 0x14095bec4
0x14095beb0 mov rcx, qword ptr [rax]
0x14095beb3 call 0x14008e180
0x14095beb8 mov rdx, qword ptr [rsp + 0x48]
0x14095bebd mov qword ptr [r11], rdx
0x14095bec0 mov qword ptr [r11 + 8], rcx
0x14095bec4 mov qword ptr [rax], rdx
0x14095bec7 add rsp, 0x60
0x14095becb pop rbp
0x14095becc ret 
0x14095becd add rsp, 0x60
0x14095bed1 pop rbp
0x14095bed2 ret 
0x14095bed3 mov qword ptr [rsp + 8], rax
0x14095bed8 mov qword ptr [rsp + 0x10], rbx
0x14095bedd mov qword ptr [rsp + 0x18], rcx
0x14095bee2 mov qword ptr [rsp + 0x20], rdi
0x14095bee7 mov qword ptr [rsp + 0x28], rsi
0x14095beec mov qword ptr [rsp + 0x30], r8
0x14095bef1 mov qword ptr [rsp + 0x38], r9
0x14095bef6 mov qword ptr [rsp + 0x40], r10
0x14095befb mov qword ptr [rsp + 0x48], r11
0x14095bf00 call 0x14008c6e0
0x14095bf05 mov rax, qword ptr [rsp + 8]
0x14095bf0a mov rbx, qword ptr [rsp + 0x10]
0x14095bf0f mov rcx, qword ptr [rsp + 0x18]
0x14095bf14 mov rdi, qword ptr [rsp + 0x20]
0x14095bf19 mov rsi, qword ptr [rsp + 0x28]
0x14095bf1e mov r8, qword ptr [rsp + 0x30]
0x14095bf23 mov r9, qword ptr [rsp + 0x38]
0x14095bf28 mov r10, qword ptr [rsp + 0x40]
0x14095bf2d mov r11, qword ptr [rsp + 0x48]
0x14095bf32 jmp 0x14095bca0