0x1407f8840 lea r12, [rsp - 0x108]
0x1407f8848 cmp r12, qword ptr [r14 + 0x10]
0x1407f884c jbe 0x1407f8e33
0x1407f8852 push rbp
0x1407f8853 mov rbp, rsp
0x1407f8856 sub rsp, 0x180
0x1407f885d mov qword ptr [rsp + 0x190], rax
0x1407f8865 mov qword ptr [rsp + 0x1a8], rdi
0x1407f886d mov qword ptr [rsp + 0x198], rbx
0x1407f8875 mov qword ptr [rsp + 0x1a0], rcx
0x1407f887d nop dword ptr [rax]
0x1407f8880 call 0x1407f8760
0x1407f8885 test rbx, rbx
0x1407f8888 jne 0x1407f8e1e
0x1407f888e mov rcx, qword ptr [rsp + 0x190]
0x1407f8896 mov rcx, qword ptr [rcx]
0x1407f8899 mov rbx, rax
0x1407f889c mov rax, rcx
0x1407f889f nop 
0x1407f88a0 call 0x1401d0fc0
0x1407f88a5 test rdi, rdi
0x1407f88a8 je 0x1407f893a
0x1407f88ae movups xmmword ptr [rsp + 0x158], xmm15
0x1407f88b7 je 0x1407f88bd
0x1407f88b9 mov rdi, qword ptr [rdi + 8]
0x1407f88bd mov qword ptr [rsp + 0x158], rdi
0x1407f88c5 mov qword ptr [rsp + 0x160], rsi
0x1407f88cd lea rax, [rip + 0x2115a3]
0x1407f88d4 mov ebx, 0x17
0x1407f88d9 lea rcx, [rsp + 0x158]
0x1407f88e1 mov edi, 1
0x1407f88e6 mov esi, edi
0x1407f88e8 call 0x14013d080
0x1407f88ed test rax, rax
0x1407f88f0 jne 0x1407f8925
0x1407f88f2 nop 
0x1407f88f3 mov eax, 0x10
0x1407f88f8 lea rbx, [rip + 0x15c7c01]
0x1407f88ff mov ecx, 1
0x1407f8904 call 0x140022a00
0x1407f8909 mov qword ptr [rax + 8], 0x17
0x1407f8911 lea rdx, [rip + 0x21155f]
0x1407f8918 mov qword ptr [rax], rdx
0x1407f891b mov rbx, rax
0x1407f891e lea rax, [rip + 0x1687fe3]
0x1407f8925 xor ecx, ecx
0x1407f8927 mov rdi, rax
0x1407f892a mov rsi, rbx
0x1407f892d xor eax, eax
0x1407f892f mov ebx, ecx
0x1407f8931 add rsp, 0x180
0x1407f8938 pop rbp
0x1407f8939 ret 
0x1407f893a mov qword ptr [rsp + 0x100], rcx
0x1407f8942 mov qword ptr [rsp + 0xf8], rbx
0x1407f894a mov qword ptr [rsp + 0x130], rax
0x1407f8952 lea rax, [rip + 0x15a4167]
0x1407f8959 mov ebx, 0x10
0x1407f895e mov ecx, ebx
0x1407f8960 call 0x140088fc0
0x1407f8965 mov qword ptr [rsp + 0x178], rax
0x1407f896d mov ebx, 0x10
0x1407f8972 mov ecx, ebx
0x1407f8974 call 0x140169ae0
0x1407f8979 nop dword ptr [rax]
0x1407f8980 test rbx, rbx
0x1407f8983 jne 0x1407f8e09
0x1407f8989 lea rax, [rip + 0x15a4130]
0x1407f8990 mov ebx, 0xc
0x1407f8995 mov ecx, ebx
0x1407f8997 call 0x140088fc0
0x1407f899c mov qword ptr [rsp + 0x170], rax
0x1407f89a4 mov ebx, 0xc
0x1407f89a9 mov ecx, ebx
0x1407f89ab call 0x140169ae0
0x1407f89b0 test rbx, rbx
0x1407f89b3 jne 0x1407f8df4
0x1407f89b9 lea rdx, [rip + 0x204a06]
0x1407f89c0 mov qword ptr [rsp], rdx
0x1407f89c4 mov qword ptr [rsp + 8], 0x10
0x1407f89cd lea rax, [rip + 0xc8a2cc]
0x1407f89d4 lea rbx, [rip + 0x1697d6d]
0x1407f89db mov rcx, qword ptr [rsp + 0x130]
0x1407f89e3 mov rdi, qword ptr [rsp + 0xf8]
0x1407f89eb mov rsi, qword ptr [rsp + 0x100]
0x1407f89f3 mov r8, qword ptr [rsp + 0x178]
0x1407f89fb mov r9d, 0x10
0x1407f8a01 mov r10d, r9d
0x1407f8a04 mov r11d, 0x20
0x1407f8a0a call 0x14085fca0
0x1407f8a0f test rdi, rdi
0x1407f8a12 je 0x1407f8aa4
0x1407f8a18 movups xmmword ptr [rsp + 0x148], xmm15
0x1407f8a21 je 0x1407f8a27
0x1407f8a23 mov rdi, qword ptr [rdi + 8]
0x1407f8a27 mov qword ptr [rsp + 0x148], rdi
0x1407f8a2f mov qword ptr [rsp + 0x150], rsi
0x1407f8a37 lea rax, [rip + 0x211450]
0x1407f8a3e mov ebx, 0x17
0x1407f8a43 lea rcx, [rsp + 0x148]
0x1407f8a4b mov edi, 1
0x1407f8a50 mov esi, edi
0x1407f8a52 call 0x14013d080
0x1407f8a57 test rax, rax
0x1407f8a5a jne 0x1407f8a8f
0x1407f8a5c nop 
0x1407f8a5d mov eax, 0x10
0x1407f8a62 lea rbx, [rip + 0x15c7a97]
0x1407f8a69 mov ecx, 1
0x1407f8a6e call 0x140022a00
0x1407f8a73 mov qword ptr [rax + 8], 0x17
0x1407f8a7b lea rdx, [rip + 0x21140c]
0x1407f8a82 mov qword ptr [rax], rdx
0x1407f8a85 mov rbx, rax
0x1407f8a88 lea rax, [rip + 0x1687e79]
0x1407f8a8f xor ecx, ecx
0x1407f8a91 mov rdi, rax
0x1407f8a94 mov rsi, rbx
0x1407f8a97 xor eax, eax
0x1407f8a99 mov ebx, ecx
0x1407f8a9b add rsp, 0x180
0x1407f8aa2 pop rbp
0x1407f8aa3 ret 
0x1407f8aa4 call 0x14024b360
0x1407f8aa9 test rcx, rcx
0x1407f8aac jne 0x1407f8ddf
0x1407f8ab2 call 0x140112120
0x1407f8ab7 nop word ptr [rax + rax]
0x1407f8ac0 test rcx, rcx
0x1407f8ac3 jne 0x1407f8dca
0x1407f8ac9 mov rdx, qword ptr [rax + 0x30]
0x1407f8acd mov r10, qword ptr [rsp + 0x198]
0x1407f8ad5 mov qword ptr [rsp], r10
0x1407f8ad9 mov r10, qword ptr [rsp + 0x1a0]
0x1407f8ae1 mov qword ptr [rsp + 8], r10
0x1407f8ae6 mov r10, qword ptr [rsp + 0x1a8]
0x1407f8aee mov qword ptr [rsp + 0x10], r10
0x1407f8af3 movups xmmword ptr [rsp + 0x18], xmm15
0x1407f8af9 mov qword ptr [rsp + 0x28], 0
0x1407f8b02 mov rax, rbx
0x1407f8b05 xor ebx, ebx
0x1407f8b07 xor ecx, ecx
0x1407f8b09 mov edi, ecx
0x1407f8b0b mov rsi, qword ptr [rsp + 0x170]
0x1407f8b13 mov r8d, 0xc
0x1407f8b19 mov r9d, r8d
0x1407f8b1c call rdx
0x1407f8b1e mov qword ptr [rsp + 0x140], rax
0x1407f8b26 mov qword ptr [rsp + 0x118], rbx
0x1407f8b2e mov rdx, qword ptr [rsp + 0x190]
0x1407f8b36 mov rdx, qword ptr [rdx]
0x1407f8b39 mov rdx, qword ptr [rdx + 0x28]
0x1407f8b3d lea r10, [rsp + 0x6b]
0x1407f8b42 movups xmmword ptr [r10], xmm15
0x1407f8b46 movups xmmword ptr [r10 + 0x10], xmm15
0x1407f8b4b movups xmmword ptr [r10 + 0x20], xmm15
0x1407f8b50 movups xmmword ptr [r10 + 0x30], xmm15
0x1407f8b55 movups xmmword ptr [r10 + 0x40], xmm15
0x1407f8b5a movups xmmword ptr [r10 + 0x50], xmm15
0x1407f8b5f movups xmmword ptr [r10 + 0x60], xmm15
0x1407f8b64 movups xmmword ptr [r10 + 0x70], xmm15
0x1407f8b69 movups xmmword ptr [r10 + 0x75], xmm15
0x1407f8b6e mov rdi, qword ptr [rdx + 0x18]
0x1407f8b72 mov rdx, qword ptr [rdx + 0x10]
0x1407f8b76 cmp rdi, 0x85
0x1407f8b7d ja 0x1407f8b89
0x1407f8b7f mov rcx, rdi
0x1407f8b82 lea rsi, [rsp + 0x6b]
0x1407f8b87 jmp 0x1407f8bc6
0x1407f8b89 mov qword ptr [rsp + 0x120], rdi
0x1407f8b91 mov qword ptr [rsp + 0x168], rdx
0x1407f8b99 mov rax, r10
0x1407f8b9c mov rbx, rdi
0x1407f8b9f mov ecx, 0x85
0x1407f8ba4 lea rsi, [rip + 0x15a3f15]
0x1407f8bab call 0x1400890a0
0x1407f8bb0 mov rcx, qword ptr [rsp + 0x120]
0x1407f8bb8 mov rdx, qword ptr [rsp + 0x168]
0x1407f8bc0 mov rdi, rbx
0x1407f8bc3 mov rsi, rax
0x1407f8bc6 mov qword ptr [rsp + 0xf0], rdi
0x1407f8bce mov qword ptr [rsp + 0x128], rsi
0x1407f8bd6 mov rax, rsi
0x1407f8bd9 mov rbx, rdx
0x1407f8bdc nop dword ptr [rax]
0x1407f8be0 call 0x14008e8c0
0x1407f8be5 mov rdx, qword ptr [rsp + 0x118]
0x1407f8bed mov rsi, qword ptr [rsp + 0xf0]
0x1407f8bf5 lea rcx, [rsi + rdx]
0x1407f8bf9 lea rcx, [rcx + 0x1d]
0x1407f8bfd mov qword ptr [rsp + 0x110], rcx
0x1407f8c05 lea rax, [rip + 0x15a3eb4]
0x1407f8c0c xor ebx, ebx
0x1407f8c0e call 0x140088fc0
0x1407f8c13 mov rcx, qword ptr [rsp + 0x110]
0x1407f8c1b nop dword ptr [rax + rax]
0x1407f8c20 test rcx, rcx
0x1407f8c23 jne 0x1407f8c3a
0x1407f8c25 mov ebx, 1
0x1407f8c2a xor ecx, ecx
0x1407f8c2c mov edi, ebx
0x1407f8c2e lea rsi, [rip + 0x15a3e8b]
0x1407f8c35 call 0x1400890a0
0x1407f8c3a mov byte ptr [rax], 2
0x1407f8c3d mov rbx, qword ptr [rsp + 0xf0]
0x1407f8c45 lea rdx, [rbx + 1]
0x1407f8c49 cmp rcx, rdx
0x1407f8c4c jae 0x1407f8c70
0x1407f8c4e mov rdi, rbx
0x1407f8c51 lea rsi, [rip + 0x15a3e68]
0x1407f8c58 mov rbx, rdx
0x1407f8c5b nop dword ptr [rax + rax]
0x1407f8c60 call 0x1400890a0
0x1407f8c65 mov rdx, rbx
0x1407f8c68 mov rbx, qword ptr [rsp + 0xf0]
0x1407f8c70 mov qword ptr [rsp + 0x110], rcx
0x1407f8c78 mov qword ptr [rsp + 0x108], rdx
0x1407f8c80 mov qword ptr [rsp + 0x138], rax
0x1407f8c88 inc rax
0x1407f8c8b mov rcx, rbx
0x1407f8c8e mov rbx, qword ptr [rsp + 0x128]
0x1407f8c96 call 0x14008e8c0
0x1407f8c9b mov rbx, qword ptr [rsp + 0x108]
0x1407f8ca3 lea rdx, [rbx + 0x10]
0x1407f8ca7 mov rcx, qword ptr [rsp + 0x110]
0x1407f8caf cmp rcx, rdx
0x1407f8cb2 jb 0x1407f8cbe
0x1407f8cb4 mov rax, qword ptr [rsp + 0x138]
0x1407f8cbc jmp 0x1407f8ce5
0x1407f8cbe mov rax, qword ptr [rsp + 0x138]
0x1407f8cc6 mov rbx, rdx
0x1407f8cc9 mov edi, 0x10
0x1407f8cce lea rsi, [rip + 0x15a3deb]
0x1407f8cd5 call 0x1400890a0
0x1407f8cda mov rdx, rbx
0x1407f8cdd mov rbx, qword ptr [rsp + 0x108]
0x1407f8ce5 lea r8, [rax + rbx]
0x1407f8ce9 mov r9, qword ptr [rsp + 0x178]
0x1407f8cf1 movups xmm0, xmmword ptr [r9]
0x1407f8cf5 movups xmmword ptr [r8], xmm0
0x1407f8cf9 lea rbx, [rdx + 0xc]
0x1407f8cfd nop dword ptr [rax]
0x1407f8d00 cmp rcx, rbx
0x1407f8d03 jae 0x1407f8d26
0x1407f8d05 mov qword ptr [rsp + 0x108], rdx
0x1407f8d0d mov edi, 0xc
0x1407f8d12 lea rsi, [rip + 0x15a3da7]
0x1407f8d19 call 0x1400890a0
0x1407f8d1e mov rdx, qword ptr [rsp + 0x108]
0x1407f8d26 mov r8, qword ptr [rsp + 0x170]
0x1407f8d2e mov r9, qword ptr [r8]
0x1407f8d31 mov r8d, dword ptr [r8 + 8]
0x1407f8d35 mov qword ptr [rax + rdx], r9
0x1407f8d39 mov dword ptr [rax + rdx + 8], r8d
0x1407f8d3e mov rdi, qword ptr [rsp + 0x118]
0x1407f8d46 lea rdx, [rbx + rdi]
0x1407f8d4a cmp rcx, rdx
0x1407f8d4d jae 0x1407f8d79
0x1407f8d4f mov qword ptr [rsp + 0x108], rbx
0x1407f8d57 mov rbx, rdx
0x1407f8d5a lea rsi, [rip + 0x15a3d5f]
0x1407f8d61 call 0x1400890a0
0x1407f8d66 mov rdi, qword ptr [rsp + 0x118]
0x1407f8d6e mov rdx, rbx
0x1407f8d71 mov rbx, qword ptr [rsp + 0x108]
0x1407f8d79 mov qword ptr [rsp + 0x110], rcx
0x1407f8d81 mov qword ptr [rsp + 0x108], rdx
0x1407f8d89 mov qword ptr [rsp + 0x138], rax
0x1407f8d91 add rax, rbx
0x1407f8d94 mov rbx, qword ptr [rsp + 0x140]
0x1407f8d9c mov rcx, rdi
0x1407f8d9f nop 
0x1407f8da0 call 0x14008e8c0
0x1407f8da5 mov rax, qword ptr [rsp + 0x138]
0x1407f8dad mov rbx, qword ptr [rsp + 0x108]
0x1407f8db5 mov rcx, qword ptr [rsp + 0x110]
0x1407f8dbd xor edi, edi
0x1407f8dbf xor esi, esi
0x1407f8dc1 add rsp, 0x180
0x1407f8dc8 pop rbp
0x1407f8dc9 ret 
0x1407f8dca xor eax, eax
0x1407f8dcc xor ebx, ebx
0x1407f8dce mov rsi, rdi
0x1407f8dd1 mov rdi, rcx
0x1407f8dd4 mov ecx, ebx
0x1407f8dd6 add rsp, 0x180
0x1407f8ddd pop rbp
0x1407f8dde ret 
0x1407f8ddf xor eax, eax
0x1407f8de1 xor ebx, ebx
0x1407f8de3 mov rsi, rdi
0x1407f8de6 mov rdi, rcx
0x1407f8de9 mov ecx, ebx
0x1407f8deb add rsp, 0x180
0x1407f8df2 pop rbp
0x1407f8df3 ret 
0x1407f8df4 xor eax, eax
0x1407f8df6 mov rdi, rbx
0x1407f8df9 mov rsi, rcx
0x1407f8dfc xor ebx, ebx
0x1407f8dfe mov ecx, ebx
0x1407f8e00 add rsp, 0x180
0x1407f8e07 pop rbp
0x1407f8e08 ret 
0x1407f8e09 xor eax, eax
0x1407f8e0b mov rdi, rbx
0x1407f8e0e mov rsi, rcx
0x1407f8e11 xor ebx, ebx
0x1407f8e13 mov ecx, ebx
0x1407f8e15 add rsp, 0x180
0x1407f8e1c pop rbp
0x1407f8e1d ret 
0x1407f8e1e xor eax, eax
0x1407f8e20 mov rdi, rbx
0x1407f8e23 mov rsi, rcx
0x1407f8e26 xor ebx, ebx
0x1407f8e28 mov ecx, ebx
0x1407f8e2a add rsp, 0x180
0x1407f8e31 pop rbp
0x1407f8e32 ret 
0x1407f8e33 mov qword ptr [rsp + 8], rax
0x1407f8e38 mov qword ptr [rsp + 0x10], rbx
0x1407f8e3d mov qword ptr [rsp + 0x18], rcx
0x1407f8e42 mov qword ptr [rsp + 0x20], rdi
0x1407f8e47 call 0x14008c6e0
0x1407f8e4c mov rax, qword ptr [rsp + 8]
0x1407f8e51 mov rbx, qword ptr [rsp + 0x10]
0x1407f8e56 mov rcx, qword ptr [rsp + 0x18]
0x1407f8e5b mov rdi, qword ptr [rsp + 0x20]
0x1407f8e60 jmp 0x1407f8840